package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpiryMailSenderConfigTest {

    private final ExpiryMailSenderConfig config = new ExpiryMailSenderConfig();

    @Test
    @DisplayName("API 키가 비어 있으면 로그만 남기는 구현을 쓴다")
    void usesLoggingWithoutApiKey() {
        assertThat(config.expiryMailSender(" ", "https://api.resend.com", "f <a@b.c>", "http://x", 2000, 5000))
                .isInstanceOf(LoggingExpiryMailSender.class);
    }

    @Test
    @DisplayName("API 키가 있으면 Resend 구현을 쓴다")
    void usesResendWithApiKey() {
        assertThat(config.expiryMailSender("re_key", "https://api.resend.com", "f <a@b.c>", "http://x", 2000, 5000))
                .isInstanceOf(ResendExpiryMailSender.class);
    }
}
