package com.back.facepick.album.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.album.domain.exception.AlbumExpiryNoticeNotPendingException;
import com.back.facepick.album.fixture.AlbumExpiryNoticeFixture;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlbumExpiryNoticeTest {

    private static final LocalDateTime NOW = AlbumExpiryNoticeFixture.NOW;
    private static final Duration LEASE = Duration.ofMinutes(5);

    @Test
    @DisplayName("생성하면 PENDING, 시도 0번, 바로 보낼 수 있다")
    void create() {
        // when
        AlbumExpiryNotice notice = AlbumExpiryNotice.create(10L, 1L, NOW);

        // then
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.PENDING);
        assertThat(notice.getAttempts()).isZero();
        assertThat(notice.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("멱등 키는 알림마다 무작위로 정해지고 다시 불러도 같다")
    void idempotencyKeyIsStablePerNotice() {
        // given
        AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(42L, 10L, 1L);

        // when & then
        assertThat(notice.idempotencyKey()).startsWith("album-expiry-notice-").isEqualTo(notice.idempotencyKey());
    }

    @Test
    @DisplayName("멱등 키는 알림 ID 로 정하지 않는다 - DB 를 초기화해 같은 ID 가 다시 나와도 Resend 키가 겹치지 않게")
    void idempotencyKeyDoesNotDependOnId() {
        // given
        AlbumExpiryNotice before = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);
        AlbumExpiryNotice afterReset = AlbumExpiryNoticeFixture.pending(1L, 20L, 2L);

        // when & then
        assertThat(before.idempotencyKey()).isNotEqualTo(afterReset.idempotencyKey());
    }

    @Nested
    @DisplayName("선점")
    class Claim {
        @Test
        @DisplayName("시도 횟수를 올리고 임대 시간만큼 다음 시도를 미룬다")
        void incrementsAttemptsAndLeases() {
            // given
            AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);

            // when
            notice.claim(NOW, LEASE);

            // then
            assertThat(notice.getAttempts()).isEqualTo(1);
            assertThat(notice.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(5));
        }

        @Test
        @DisplayName("PENDING 이 아니면 선점할 수 없다")
        void rejectsNonPending() {
            // given
            AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);
            notice.markFailed("400");

            // when & then
            assertThatThrownBy(() -> notice.claim(NOW, LEASE)).isInstanceOf(AlbumExpiryNoticeNotPendingException.class);
        }
    }

    @Nested
    @DisplayName("성공")
    class MarkSent {
        @Test
        @DisplayName("SENT 로 바꾸고 Resend 메일 ID 와 보낸 시각을 남긴다")
        void marksSent() {
            // given
            AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);
            notice.claim(NOW, LEASE);

            // when
            notice.markSent("re_123", NOW.plusSeconds(1));

            // then
            assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.SENT);
            assertThat(notice.getProviderMessageId()).isEqualTo("re_123");
            assertThat(notice.getSentAt()).isEqualTo(NOW.plusSeconds(1));
        }

        @Test
        @DisplayName("이미 SENT 면 다시 바꿀 수 없다")
        void rejectsAlreadySent() {
            // given
            AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);
            notice.markSent("re_123", NOW);

            // when & then
            assertThatThrownBy(() -> notice.markSent("re_456", NOW))
                    .isInstanceOf(AlbumExpiryNoticeNotPendingException.class);
        }
    }

    @Nested
    @DisplayName("일시 실패")
    class MarkRetryableFailure {
        @Test
        @DisplayName("시도 횟수에 따라 1분·5분·30분·2시간 뒤로 미룬다")
        void backsOff() {
            // given
            AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);
            Duration[] expected = {
                Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2)
            };

            for (Duration delay : expected) {
                // when
                notice.claim(NOW, LEASE);
                notice.markRetryableFailure("503", NOW);

                // then
                assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.PENDING);
                assertThat(notice.getNextAttemptAt()).isEqualTo(NOW.plus(delay));
            }
            assertThat(notice.getLastError()).isEqualTo("503");
        }

        @Test
        @DisplayName("5번째 시도도 실패하면 FAILED")
        void failsAtMaxAttempts() {
            // given
            AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);
            for (int i = 1; i < AlbumExpiryNotice.MAX_ATTEMPTS; i++) {
                notice.claim(NOW, LEASE);
                notice.markRetryableFailure("503", NOW);
            }
            notice.claim(NOW, LEASE);

            // when
            notice.markRetryableFailure("timeout", NOW);

            // then
            assertThat(notice.getAttempts()).isEqualTo(5);
            assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.FAILED);
            assertThat(notice.getLastError()).isEqualTo("timeout");
        }

        @Test
        @DisplayName("사유가 500자를 넘으면 잘라서 남긴다")
        void truncatesLongError() {
            // given
            AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);
            notice.claim(NOW, LEASE);

            // when
            notice.markRetryableFailure("x".repeat(600), NOW);

            // then
            assertThat(notice.getLastError()).hasSize(500);
        }
    }

    @Test
    @DisplayName("영구 실패 - FAILED 로 바꾸고 사유를 남긴다")
    void markFailed() {
        // given
        AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);

        // when
        notice.markFailed("422 invalid email");

        // then
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.FAILED);
        assertThat(notice.getLastError()).isEqualTo("422 invalid email");
    }

    @Test
    @DisplayName("건너뜀 - SKIPPED 로 바꾸고 사유를 남긴다")
    void markSkipped() {
        // given
        AlbumExpiryNotice notice = AlbumExpiryNoticeFixture.pending(1L, 10L, 1L);

        // when
        notice.markSkipped("앨범이 없거나 이미 만료됨");

        // then
        assertThat(notice.getStatus()).isEqualTo(AlbumExpiryNoticeStatus.SKIPPED);
        assertThat(notice.getLastError()).isEqualTo("앨범이 없거나 이미 만료됨");
    }
}
