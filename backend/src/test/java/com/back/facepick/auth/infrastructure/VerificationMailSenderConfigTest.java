package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.global.infrastructure.mail.ResendClient;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class VerificationMailSenderConfigTest {

    private final VerificationMailSenderConfig config = new VerificationMailSenderConfig();
    private final ResendClient resendClient =
            new ResendClient(RestClient.create(), "f <a@b.c>", CircuitBreaker.ofDefaults("test"));

    @Test
    @DisplayName("Bulkhead 설정값이 그대로 들어간다")
    void bulkheadConfig() {
        // when
        BulkheadConfig built = config.verificationMailBulkhead(5, 0).getBulkheadConfig();

        // then
        assertThat(built.getMaxConcurrentCalls()).isEqualTo(5);
        assertThat(built.getMaxWaitDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("API 키가 비어 있으면 코드를 로그로 남기는 구현을 쓴다")
    void usesLoggingWithoutApiKey() {
        assertThat(config.verificationMailSender(" ", resendClient, Bulkhead.ofDefaults("t")))
                .isInstanceOf(LoggingVerificationMailSender.class);
    }

    @Test
    @DisplayName("API 키가 있으면 Resend 구현을 쓴다")
    void usesResendWithApiKey() {
        assertThat(config.verificationMailSender("re_key", resendClient, Bulkhead.ofDefaults("t")))
                .isInstanceOf(ResendVerificationMailSender.class);
    }
}
