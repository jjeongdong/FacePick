package com.back.facepick.album.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailSendOutcomeTest {

    @Test
    @DisplayName("보내지 않은 결과는 다시 시도할 시각만 담는다")
    void notAttempted() {
        // given
        LocalDateTime retryAt = LocalDateTime.of(2026, 9, 1, 12, 1);

        // when
        MailSendOutcome outcome = MailSendOutcome.notAttempted(retryAt);

        // then
        assertThat(outcome.type()).isEqualTo(MailSendOutcome.Type.NOT_ATTEMPTED);
        assertThat(outcome.retryAt()).isEqualTo(retryAt);
        assertThat(outcome.providerMessageId()).isNull();
        assertThat(outcome.error()).isNull();
    }

    @Test
    @DisplayName("보낸 결과·실패 결과에는 다시 시도할 시각이 없다")
    void otherTypesHaveNoRetryAt() {
        assertThat(MailSendOutcome.sent("re_1").retryAt()).isNull();
        assertThat(MailSendOutcome.retryable("503").retryAt()).isNull();
        assertThat(MailSendOutcome.permanent("422").retryAt()).isNull();
    }

    @Test
    @DisplayName("메일 구현은 따로 알리지 않으면 언제나 사용 가능하다")
    void availableByDefault() {
        // given
        ExpiryMailSender sender = mail -> MailSendOutcome.sent("re_1");

        // when & then
        assertThat(sender.isAvailable()).isTrue();
    }
}
