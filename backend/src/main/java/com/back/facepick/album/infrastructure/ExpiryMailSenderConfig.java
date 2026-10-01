package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.ExpiryMailSender;
import com.back.facepick.global.infrastructure.mail.ResendClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExpiryMailSenderConfig {

    @Bean
    public ExpiryMailSender expiryMailSender(
            @Value("${facepick.mail.resend-api-key}") String resendApiKey,
            @Value("${facepick.mail.app-base-url}") String appBaseUrl,
            ResendClient resendClient,
            @Value("${facepick.mail.circuit-breaker.open-seconds}") long openSeconds) {
        if (resendApiKey.isBlank()) {
            return new LoggingExpiryMailSender();
        }
        return new ResendExpiryMailSender(
                resendClient, new ExpiryMailTemplate(appBaseUrl), Duration.ofSeconds(openSeconds));
    }
}
