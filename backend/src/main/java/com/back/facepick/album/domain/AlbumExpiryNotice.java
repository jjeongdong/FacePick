package com.back.facepick.album.domain;

import com.back.facepick.album.domain.exception.AlbumExpiryNoticeNotPendingException;
import com.back.facepick.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 앨범 만료 알림 메일 한 건 (앨범 × 멤버).
// 발송기가 앨범을 한 번에 모아 조회하므로 앨범은 연관관계 없이 ID 로 둔다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "album_expiry_notices")
public class AlbumExpiryNotice extends BaseTimeEntity {
    // 실패한 시도 번호(1부터) → 다음 시도까지 기다릴 시간. 다 쓰고도 실패하면 FAILED.
    // 합(약 2.6시간)이 Resend Idempotency-Key 보관 기간(24시간)보다 짧아야 재시도가 중복 발송되지 않는다.
    private static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));
    public static final int MAX_ATTEMPTS = RETRY_DELAYS.size() + 1;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String IDEMPOTENCY_KEY_PREFIX = "album-expiry-notice-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    @Column(name = "album_id", nullable = false)
    private Long albumId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlbumExpiryNoticeStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // 스캐너의 INSERT ... SELECT 는 DB 기본값(gen_random_uuid())으로 채운다.
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    private AlbumExpiryNotice(Long albumId, Long userId, LocalDateTime now) {
        this.albumId = albumId;
        this.userId = userId;
        this.status = AlbumExpiryNoticeStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.idempotencyKey = UUID.randomUUID();
    }

    // 운영에서는 스캐너의 INSERT ... SELECT 가 행을 만든다. 같은 초깃값을 쓴다.
    public static AlbumExpiryNotice create(Long albumId, Long userId, LocalDateTime now) {
        return new AlbumExpiryNotice(albumId, userId, now);
    }

    // 임대 동안에는 다른 발송기(다른 서버)가 이 행을 다시 잡지 않는다.
    public void claim(LocalDateTime now, Duration lease) {
        requirePending();
        this.attempts++;
        this.nextAttemptAt = now.plus(lease);
    }

    public void markSent(String providerMessageId, LocalDateTime now) {
        requirePending();
        this.status = AlbumExpiryNoticeStatus.SENT;
        this.providerMessageId = providerMessageId;
        this.sentAt = now;
    }

    public void markRetryableFailure(String error, LocalDateTime now) {
        requirePending();
        this.lastError = truncate(error);
        if (attempts >= MAX_ATTEMPTS) {
            this.status = AlbumExpiryNoticeStatus.FAILED;
            return;
        }
        this.nextAttemptAt = now.plus(RETRY_DELAYS.get(Math.max(attempts, 1) - 1));
    }

    public void markFailed(String error) {
        requirePending();
        this.status = AlbumExpiryNoticeStatus.FAILED;
        this.lastError = truncate(error);
    }

    public void markSkipped(String reason) {
        requirePending();
        this.status = AlbumExpiryNoticeStatus.SKIPPED;
        this.lastError = truncate(reason);
    }

    // 서킷이 열려 호출하지 않은 선점을 되돌린다. 보내지 않았으니 시도 횟수에 넣지 않는다.
    public void releaseUnattempted(LocalDateTime retryAt) {
        requirePending();
        this.attempts = Math.max(attempts - 1, 0);
        this.nextAttemptAt = retryAt;
    }

    // 재시도해도 같은 값이어야 Resend 가 이미 보낸 메일을 다시 보내지 않는다.
    // notice_id 가 아니라 행마다 무작위 값을 써서, DB 초기화나 다른 환경과 Resend 계정을 함께 써도 키가 겹치지 않는다.
    public String idempotencyKey() {
        return IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
    }

    private void requirePending() {
        if (status != AlbumExpiryNoticeStatus.PENDING) {
            throw new AlbumExpiryNoticeNotPendingException();
        }
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_ERROR_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_ERROR_LENGTH);
    }
}
