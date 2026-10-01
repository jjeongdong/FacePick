package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.global.infrastructure.mail.ResendClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ExpiryMailSenderConfigTest {

    private final ExpiryMailSenderConfig config = new ExpiryMailSenderConfig();
    private final ResendClient resendClient =
            new ResendClient(RestClient.create(), "f <a@b.c>", CircuitBreaker.ofDefaults("test"));

    @Test
    @DisplayName("API 키가 비어 있으면 로그만 남기는 구현을 쓴다")
    void usesLoggingWithoutApiKey() {
        assertThat(config.expiryMailSender(" ", "http://x", resendClient, 60))
                .isInstanceOf(LoggingExpiryMailSender.class);
    }

    @Test
    @DisplayName("API 키가 있으면 Resend 구현을 쓴다")
    void usesResendWithApiKey() {
        assertThat(config.expiryMailSender("re_key", "http://x", resendClient, 60))
                .isInstanceOf(ResendExpiryMailSender.class);
    }
}
