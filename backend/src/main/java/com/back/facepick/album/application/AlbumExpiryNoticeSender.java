package com.back.facepick.album.application;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumExpiryNotice;
import com.back.facepick.album.domain.AlbumExpiryNoticeRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.ExpiryMailSender;
import com.back.facepick.album.domain.MailSendOutcome;
import com.back.facepick.auth.application.AuthQueryApi;
import com.back.facepick.auth.application.dto.api.CredentialInfo;
import com.back.facepick.global.config.scheduling.SchedulerNames;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 외부 API(Resend)를 기다리는 동안 DB 커넥션·행 잠금을 잡지 않도록, 선점과 결과 저장만 짧은 트랜잭션으로 나눈다.
// 선점 → (트랜잭션 밖) 발송 → 행마다 결과 저장. 발송 중 서버가 죽으면 임대가 끝난 뒤 같은 멱등 키로 다시 보낸다.
// 메일 서비스가 장애 중이면(서킷 OPEN) 선점하지 않고, 배치 도중 열려 보내지 못한 행은 시도 횟수를 되돌려 반납한다.
@Slf4j
@Service
public class AlbumExpiryNoticeSender {
    private final AlbumExpiryNoticeRepository albumExpiryNoticeRepository;
    private final AlbumRepository albumRepository;
    private final AuthQueryApi authQueryApi;
    private final ExpiryMailSender expiryMailSender;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final Duration lease;
    private final long sendPauseMillis;

    public AlbumExpiryNoticeSender(
            AlbumExpiryNoticeRepository albumExpiryNoticeRepository,
            AlbumRepository albumRepository,
            AuthQueryApi authQueryApi,
            ExpiryMailSender expiryMailSender,
            PlatformTransactionManager transactionManager,
            @Value("${facepick.album.expiry-notice.batch-size}") int batchSize,
            @Value("${facepick.album.expiry-notice.lease-minutes}") long leaseMinutes,
            @Value("${facepick.album.expiry-notice.send-pause-millis}") long sendPauseMillis) {
        this.albumExpiryNoticeRepository = albumExpiryNoticeRepository;
        this.albumRepository = albumRepository;
        this.authQueryApi = authQueryApi;
        this.expiryMailSender = expiryMailSender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.lease = Duration.ofMinutes(leaseMinutes);
        this.sendPauseMillis = sendPauseMillis;
    }

    @Scheduled(
            fixedDelayString = "${facepick.album.expiry-notice.send-interval-millis}",
            scheduler = SchedulerNames.EXTERNAL)
    public void send() {
        // 메일 서비스가 장애 중이면(서킷 OPEN) 선점조차 하지 않는다. 행은 그대로 두고 다음 회차에 다시 본다.
        if (!expiryMailSender.isAvailable()) {
            log.debug("메일 서비스를 쓸 수 없어 이번 발송 회차를 건너뛴다");
            return;
        }
        List<AlbumExpiryNotice> notices = claim(LocalDateTime.now());
        if (notices.isEmpty()) {
            return;
        }
        Map<Long, Album> albums =
                albumRepository
                        .findAllByIds(notices.stream()
                                .map(AlbumExpiryNotice::getAlbumId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Album::getId, Function.identity()));
        Map<Long, CredentialInfo> credentials = authQueryApi.getInfos(
                notices.stream().map(AlbumExpiryNotice::getUserId).distinct().toList());
        for (AlbumExpiryNotice notice : notices) {
            try {
                deliver(notice, albums.get(notice.getAlbumId()), credentials.get(notice.getUserId()));
            } catch (RuntimeException e) {
                // 결과 저장 실패 등. 행은 PENDING 으로 남아 임대가 끝나면 같은 키로 다시 보낸다.
                log.error("만료 알림 처리 실패, 임대가 끝나면 다시 시도한다 noticeId={}", notice.getId(), e);
            }
        }
    }

    private List<AlbumExpiryNotice> claim(LocalDateTime now) {
        return transactionTemplate.execute(status -> {
            List<AlbumExpiryNotice> due = albumExpiryNoticeRepository.findDueForUpdate(now, batchSize);
            due.forEach(notice -> notice.claim(now, lease));
            return due;
        });
    }

    private void deliver(AlbumExpiryNotice notice, Album album, CredentialInfo credential) {
        if (album == null || !LocalDateTime.now().isBefore(album.getExpiresAt())) {
            update(notice.getId(), row -> row.markSkipped("앨범이 없거나 이미 만료됨"));
            return;
        }
        if (credential == null) {
            update(notice.getId(), row -> row.markSkipped("이메일 없음"));
            return;
        }
        MailSendOutcome outcome = sendSafely(new ExpiryMail(
                notice.idempotencyKey(), credential.email(), album.getId(), album.getTitle(), album.getExpiresAt()));
        if (outcome.type() == MailSendOutcome.Type.NOT_ATTEMPTED) {
            // 호출하지 않았으니 한도에 걸릴 일도 없어 쉬지 않고 다음 행으로 간다.
            update(notice.getId(), row -> row.releaseUnattempted(outcome.retryAt()));
            return;
        }
        pause();
        LocalDateTime now = LocalDateTime.now();
        switch (outcome.type()) {
            case SENT -> update(notice.getId(), row -> row.markSent(outcome.providerMessageId(), now));
            case RETRYABLE -> update(notice.getId(), row -> row.markRetryableFailure(outcome.error(), now));
            case PERMANENT -> update(notice.getId(), row -> row.markFailed(outcome.error()));
            case NOT_ATTEMPTED -> throw new IllegalStateException("위에서 처리했다");
        }
    }

    // 포트 계약은 예외를 던지지 않는 것이지만, 구현 버그 하나가 나머지 행을 막지 않게 일시 실패로 바꾼다.
    private MailSendOutcome sendSafely(ExpiryMail mail) {
        try {
            return expiryMailSender.send(mail);
        } catch (RuntimeException e) {
            log.error("만료 알림 메일 구현이 예외를 던졌다 key={}", mail.idempotencyKey(), e);
            return MailSendOutcome.retryable(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // Resend 는 초당 요청 수를 제한한다. 배치를 쉬지 않고 보내면 429 가 시도 횟수를 써 버려 일부 멤버가 FAILED 가 될 수 있다.
    private void pause() {
        if (sendPauseMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(sendPauseMillis);
        } catch (InterruptedException e) {
            // 종료 중이면 남은 행은 쉬지 않고 처리하고, 끝나지 않은 행은 임대가 끝난 뒤 다시 잡힌다.
            Thread.currentThread().interrupt();
        }
    }

    // 행이 없으면(앨범 삭제로 CASCADE) 할 일이 없다.
    private void update(Long noticeId, Consumer<AlbumExpiryNotice> change) {
        transactionTemplate.executeWithoutResult(
                status -> albumExpiryNoticeRepository.findById(noticeId).ifPresent(change));
    }
}
