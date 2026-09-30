package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.ExpiryMailSender;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
public class ExpiryMailSenderConfig {

    // 잘못된 값(비율 0~100 밖, 음수 등)이면 Resilience4j 가 IllegalArgumentException 을 던져 앱이 뜨지 않는다.
    @Bean
    public CircuitBreaker expiryMailCircuitBreaker(
            @Value("${facepick.mail.circuit-breaker.window-size}") int windowSize,
            @Value("${facepick.mail.circuit-breaker.minimum-calls}") int minimumCalls,
            @Value("${facepick.mail.circuit-breaker.failure-rate}") float failureRate,
            @Value("${facepick.mail.circuit-breaker.slow-call-millis}") long slowCallMillis,
            @Value("${facepick.mail.circuit-breaker.slow-call-rate}") float slowCallRate,
            @Value("${facepick.mail.circuit-breaker.open-seconds}") long openSeconds,
            @Value("${facepick.mail.circuit-breaker.half-open-calls}") int halfOpenCalls) {
        CircuitBreaker circuitBreaker = CircuitBreaker.of(
                "resend",
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(windowSize)
                        .minimumNumberOfCalls(minimumCalls)
                        .failureRateThreshold(failureRate)
                        .slowCallDurationThreshold(Duration.ofMillis(slowCallMillis))
                        .slowCallRateThreshold(slowCallRate)
                        .waitDurationInOpenState(Duration.ofSeconds(openSeconds))
                        .permittedNumberOfCallsInHalfOpenState(halfOpenCalls)
                        // 발송기는 isAvailable() 로 상태만 본다. 자동 전환이 없으면 OPEN 에서 나오지 못한다.
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build());
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
            if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                log.warn(
                        "Resend 서킷 {} — 실패율 {}% 느린 호출 {}%",
                        event.getStateTransition(), metrics.getFailureRate(), metrics.getSlowCallRate());
            } else {
                log.info("Resend 서킷 {}", event.getStateTransition());
            }
        });
        return circuitBreaker;
    }

    // 발송기가 Resend 를 기다리는 동안 스케줄러 스레드를 붙잡으므로 타임아웃을 짧게 둔다.
    @Bean
    public ExpiryMailSender expiryMailSender(
            @Value("${facepick.mail.resend-api-key}") String resendApiKey,
            @Value("${facepick.mail.base-url}") String baseUrl,
            @Value("${facepick.mail.from}") String from,
            @Value("${facepick.mail.app-base-url}") String appBaseUrl,
            @Value("${facepick.mail.connect-timeout-millis}") long connectTimeoutMillis,
            @Value("${facepick.mail.read-timeout-millis}") long readTimeoutMillis,
            CircuitBreaker expiryMailCircuitBreaker,
            @Value("${facepick.mail.circuit-breaker.open-seconds}") long openSeconds) {
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
        return new ResendExpiryMailSender(
                restClient,
                from,
                new ExpiryMailTemplate(appBaseUrl),
                expiryMailCircuitBreaker,
                Duration.ofSeconds(openSeconds));
    }
}
