package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.VerificationMailSender;
import com.back.facepick.global.infrastructure.mail.ResendClient;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationMailSenderConfig {

    // 자리가 없을 때 기다리게 하면 기다리는 동안 그 요청 스레드도 붙잡힌다. 그래서 기본은 기다리지 않고 바로 거절한다.
    @Bean
    public Bulkhead verificationMailBulkhead(
            @Value("${facepick.auth.verification-mail.bulkhead.max-concurrent-calls}") int maxConcurrentCalls,
            @Value("${facepick.auth.verification-mail.bulkhead.max-wait-millis}") long maxWaitMillis) {
        return Bulkhead.of(
                "resend-verification",
                BulkheadConfig.custom()
                        .maxConcurrentCalls(maxConcurrentCalls)
                        .maxWaitDuration(Duration.ofMillis(maxWaitMillis))
                        .build());
    }

    @Bean
    public VerificationMailSender verificationMailSender(
            @Value("${facepick.mail.resend-api-key}") String resendApiKey,
            ResendClient resendClient,
            Bulkhead verificationMailBulkhead) {
        if (resendApiKey.isBlank()) {
            return new LoggingVerificationMailSender();
        }
        return new ResendVerificationMailSender(resendClient, verificationMailBulkhead, new VerificationMailTemplate());
    }
}
