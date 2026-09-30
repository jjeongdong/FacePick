package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.MailSendOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggingExpiryMailSenderTest {

    @Test
    @DisplayName("API 키가 없어 보내지 못했으면 보낸 것으로 치지 않고 영구 실패로 남긴다 (키를 빠뜨려도 조용히 사라지지 않게)")
    void doesNotPretendToSend() {
        // given
        ExpiryMail mail = new ExpiryMail(
                "album-expiry-notice-x", "me@example.com", 13L, "제주 여행", LocalDateTime.of(2026, 10, 7, 15, 0));

        // when
        MailSendOutcome outcome = new LoggingExpiryMailSender().send(mail);

        // then
        assertThat(outcome.type()).isEqualTo(MailSendOutcome.Type.PERMANENT);
        assertThat(outcome.error()).contains("RESEND_API_KEY");
    }
}
