package com.back.facepick.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.auth.domain.exception.AuthInvalidEmailException;
import com.back.facepick.auth.domain.exception.AuthVerificationAttemptsExceededException;
import com.back.facepick.auth.domain.exception.AuthVerificationCodeExpiredException;
import com.back.facepick.auth.domain.exception.AuthVerificationResendTooSoonException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EmailVerificationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);
    private static final String CODE = "012345";

    @Test
    @DisplayName("생성하면 이메일을 정규화하고 코드는 해시로만 담으며 10분 뒤 만료된다")
    void create() {
        // when
        EmailVerification verification = EmailVerification.create(" Me@Example.com ", CODE, NOW);

        // then
        assertThat(verification.getEmail()).isEqualTo("me@example.com");
        assertThat(verification.getCodeHash())
                .isEqualTo(EmailVerification.hash(CODE))
                .isNotEqualTo(CODE);
        assertThat(verification.getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(verification.getAttempts()).isZero();
        assertThat(verification.getLastSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이메일이 비었거나 254자를 넘으면 AuthInvalidEmailException")
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> EmailVerification.create(" ", CODE, NOW))
                .isInstanceOf(AuthInvalidEmailException.class);
        assertThatThrownBy(() -> EmailVerification.create("a".repeat(250) + "@b.cd", CODE, NOW))
                .isInstanceOf(AuthInvalidEmailException.class);
    }

    @Nested
    @DisplayName("재발급")
    class Reissue {

        @Test
        @DisplayName("60초가 지나지 않았으면 AuthVerificationResendTooSoonException")
        void throwsWithinInterval() {
            // given
            EmailVerification verification = EmailVerification.create("me@example.com", CODE, NOW);

            // when & then
            assertThatThrownBy(() -> verification.reissue("999999", NOW.plusSeconds(59)))
                    .isInstanceOf(AuthVerificationResendTooSoonException.class);
        }

        @Test
        @DisplayName("60초가 지나면 새 코드로 바꾸고 만료·틀린 횟수를 새로 시작한다")
        void replacesCode() {
            // given
            EmailVerification verification = EmailVerification.create("me@example.com", CODE, NOW);
            verification.verify("111111", NOW);
            LocalDateTime later = NOW.plusSeconds(60);

            // when
            verification.reissue("999999", later);

            // then
            assertThat(verification.getCodeHash()).isEqualTo(EmailVerification.hash("999999"));
            assertThat(verification.getAttempts()).isZero();
            assertThat(verification.getExpiresAt()).isEqualTo(later.plusMinutes(10));
            assertThat(verification.getLastSentAt()).isEqualTo(later);
        }
    }

    @Nested
    @DisplayName("코드 확인")
    class Verify {

        @Test
        @DisplayName("맞으면 true 이고 틀린 횟수는 그대로다")
        void matches() {
            // given
            EmailVerification verification = EmailVerification.create("me@example.com", CODE, NOW);

            // when
            boolean matched = verification.verify(CODE, NOW.plusMinutes(9));

            // then
            assertThat(matched).isTrue();
            assertThat(verification.getAttempts()).isZero();
        }

        @Test
        @DisplayName("틀리면 false 이고 틀린 횟수가 1 늘어난다")
        void mismatchCountsAttempt() {
            // given
            EmailVerification verification = EmailVerification.create("me@example.com", CODE, NOW);

            // when
            boolean matched = verification.verify("999999", NOW);

            // then
            assertThat(matched).isFalse();
            assertThat(verification.getAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("만료 시각이 되면 맞는 코드여도 AuthVerificationCodeExpiredException")
        void throwsWhenExpired() {
            // given
            EmailVerification verification = EmailVerification.create("me@example.com", CODE, NOW);

            // when & then
            assertThatThrownBy(() -> verification.verify(CODE, NOW.plusMinutes(10)))
                    .isInstanceOf(AuthVerificationCodeExpiredException.class);
        }

        @Test
        @DisplayName("5번 틀린 뒤에는 맞는 코드여도 AuthVerificationAttemptsExceededException")
        void throwsAfterFiveMismatches() {
            // given
            EmailVerification verification = EmailVerification.create("me@example.com", CODE, NOW);
            for (int i = 0; i < 5; i++) {
                verification.verify("999999", NOW);
            }

            // when & then
            assertThatThrownBy(() -> verification.verify(CODE, NOW))
                    .isInstanceOf(AuthVerificationAttemptsExceededException.class);
            assertThat(verification.getAttempts()).isEqualTo(5);
        }

        @Test
        @DisplayName("4번 틀려도 5번째에 맞으면 통과한다")
        void passesOnFifthTry() {
            // given
            EmailVerification verification = EmailVerification.create("me@example.com", CODE, NOW);
            for (int i = 0; i < 4; i++) {
                verification.verify("999999", NOW);
            }

            // when & then
            assertThat(verification.verify(CODE, NOW)).isTrue();
        }
    }
}
