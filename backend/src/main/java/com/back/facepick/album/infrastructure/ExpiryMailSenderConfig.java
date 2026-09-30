package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.ExpiryMailSender;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ExpiryMailSenderConfig {

    // 발송기가 Resend 를 기다리는 동안 스케줄러 스레드를 붙잡으므로 타임아웃을 짧게 둔다.
    @Bean
    public ExpiryMailSender expiryMailSender(
            @Value("${facepick.mail.resend-api-key}") String resendApiKey,
            @Value("${facepick.mail.base-url}") String baseUrl,
            @Value("${facepick.mail.from}") String from,
            @Value("${facepick.mail.app-base-url}") String appBaseUrl,
            @Value("${facepick.mail.connect-timeout-millis}") long connectTimeoutMillis,
            @Value("${facepick.mail.read-timeout-millis}") long readTimeoutMillis) {
        if (resendApiKey.isBlank()) {
            return new LoggingExpiryMailSender();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resendApiKey)
                .requestFactory(requestFactory)
                .build();
        return new ResendExpiryMailSender(restClient, from, new ExpiryMailTemplate(appBaseUrl));
    }
}
