package com.back.facepick.global.infrastructure.mail;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// https://resend.com/docs/api-reference/emails/send-email
// 만료 알림(스케줄러)과 인증 메일(HTTP 요청)이 같은 서킷을 쓴다. Resend 가 아픈지는 누가 부르든 하나의 판단이라,
// 한쪽이 먼저 장애를 겪으면 다른 쪽도 타임아웃을 기다리지 않고 바로 실패한다.
// 서킷 판정: Resend 가 아플 때(타임아웃·연결 실패·5xx)만 실패로 센다. 4xx 는 요청마다의 문제라 정상으로 본다.
public class ResendClient {
    private static final int TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;
    private final String from;
    private final CircuitBreaker circuitBreaker;

    public ResendClient(RestClient restClient, String from, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.from = from;
        this.circuitBreaker = circuitBreaker;
    }

    public boolean isAvailable() {
        CircuitBreaker.State state = circuitBreaker.getState();
        return state != CircuitBreaker.State.OPEN && state != CircuitBreaker.State.FORCED_OPEN;
    }

    public ResendResult send(ResendEmail email) {
        if (!circuitBreaker.tryAcquirePermission()) {
            return ResendResult.notAttempted();
        }
        long startedAt = System.nanoTime();
        Attempt attempt;
        try {
            attempt = call(email);
        } catch (RuntimeException | Error e) {
            // 허가를 받은 뒤 기록 없이 빠져나가면 HALF_OPEN 시험 허가가 새어 서킷이 닫히지 못한다 (Error 포함).
            circuitBreaker.onError(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS, e);
            throw e;
        }
        long elapsed = System.nanoTime() - startedAt;
        switch (attempt.verdict()) {
            case HEALTHY -> circuitBreaker.onSuccess(elapsed, TimeUnit.NANOSECONDS);
            case UNHEALTHY -> circuitBreaker.onError(elapsed, TimeUnit.NANOSECONDS, attempt.cause());
            case IGNORED -> circuitBreaker.releasePermission();
        }
        return attempt.result();
    }

    private Attempt call(ResendEmail email) {
        try {
            SendEmailReply reply = restClient
                    .post()
                    .uri("/emails")
                    .headers(headers -> {
                        if (email.idempotencyKey() != null) {
                            headers.set("Idempotency-Key", email.idempotencyKey());
                        }
                    })
                    .body(new SendEmailBody(from, List.of(email.to()), email.subject(), email.text(), email.html()))
                    .retrieve()
                    .body(SendEmailReply.class);
            return new Attempt(ResendResult.sent(reply == null ? null : reply.id()), Verdict.HEALTHY, null);
        } catch (HttpStatusCodeException e) {
            return new Attempt(
                    ResendResult.httpError(e.getStatusCode().value(), e.getResponseBodyAsString()), verdictOf(e), e);
        } catch (RestClientException e) {
            // 타임아웃·연결 실패: Resend 가 받았는지 알 수 없다.
            return new Attempt(
                    ResendResult.networkError(e.getClass().getSimpleName() + ": " + e.getMessage()),
                    Verdict.UNHEALTHY,
                    e);
        }
    }

    private static Verdict verdictOf(HttpStatusCodeException e) {
        if (e.getStatusCode().value() == TOO_MANY_REQUESTS) {
            return Verdict.IGNORED;
        }
        return e.getStatusCode().is5xxServerError() ? Verdict.UNHEALTHY : Verdict.HEALTHY;
    }

    // 서킷 입장에서 이번 호출이 Resend 의 건강에 대해 말해 주는 것
    private enum Verdict {
        HEALTHY,
        UNHEALTHY,
        // 429: 한도 초과라 Resend 가 아픈 건 아니지만 성공도 아니다. 기록하지 않는다.
        IGNORED
    }

    private record Attempt(ResendResult result, Verdict verdict, Throwable cause) {}

    record SendEmailBody(String from, List<String> to, String subject, String text, String html) {}

    record SendEmailReply(String id) {}
}
