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

    public AlbumExpiryNoticeSender(
            AlbumExpiryNoticeRepository albumExpiryNoticeRepository,
            AlbumRepository albumRepository,
            AuthQueryApi authQueryApi,
            ExpiryMailSender expiryMailSender,
            PlatformTransactionManager transactionManager,
            @Value("${facepick.album.expiry-notice.batch-size}") int batchSize,
            @Value("${facepick.album.expiry-notice.lease-minutes}") long leaseMinutes) {
        this.albumExpiryNoticeRepository = albumExpiryNoticeRepository;
        this.albumRepository = albumRepository;
        this.authQueryApi = authQueryApi;
        this.expiryMailSender = expiryMailSender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.lease = Duration.ofMinutes(leaseMinutes);
    }

    @Scheduled(fixedDelayString = "${facepick.album.expiry-notice.send-interval-millis}")
    public void send() {
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
        LocalDateTime now = LocalDateTime.now();
        switch (outcome.type()) {
            case SENT -> update(notice.getId(), row -> row.markSent(outcome.providerMessageId(), now));
            case RETRYABLE -> update(notice.getId(), row -> row.markRetryableFailure(outcome.error(), now));
            case PERMANENT -> update(notice.getId(), row -> row.markFailed(outcome.error()));
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

    // 행이 없으면(앨범 삭제로 CASCADE) 할 일이 없다.
    private void update(Long noticeId, Consumer<AlbumExpiryNotice> change) {
        transactionTemplate.executeWithoutResult(
                status -> albumExpiryNoticeRepository.findById(noticeId).ifPresent(change));
    }
}
