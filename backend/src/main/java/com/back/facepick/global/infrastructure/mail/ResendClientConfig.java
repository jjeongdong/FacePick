package com.back.facepick.global.infrastructure.mail;

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

// API 키가 없어도 빈은 만든다. 키가 없을 때 무엇을 할지(로그만 남길지)는 쓰는 BC 가 정한다.
@Slf4j
@Configuration
public class ResendClientConfig {

    // 잘못된 값(비율 0~100 밖, 음수 등)이면 Resilience4j 가 IllegalArgumentException 을 던져 앱이 뜨지 않는다.
    @Bean
    public CircuitBreaker resendCircuitBreaker(
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

    // 호출하는 쪽(스케줄러 스레드, 요청 스레드)을 붙잡으므로 타임아웃을 짧게 둔다.
    @Bean
    public ResendClient resendClient(
            @Value("${facepick.mail.resend-api-key}") String resendApiKey,
            @Value("${facepick.mail.base-url}") String baseUrl,
            @Value("${facepick.mail.from}") String from,
            @Value("${facepick.mail.connect-timeout-millis}") long connectTimeoutMillis,
            @Value("${facepick.mail.read-timeout-millis}") long readTimeoutMillis,
            CircuitBreaker resendCircuitBreaker) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resendApiKey)
                .requestFactory(requestFactory)
                .build();
        return new ResendClient(restClient, from, resendCircuitBreaker);
    }
}
