package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.ExpiryMailSender;
import com.back.facepick.album.domain.MailSendOutcome;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// https://resend.com/docs/api-reference/emails/send-email
// Idempotency-Key: 24시간 동안 같은 키 + 같은 내용이면 다시 보내지 않고 처음 메일 ID 를 돌려준다.
// 서킷 브레이커: Resend 가 아플 때(타임아웃·연결 실패·5xx)만 실패로 센다. 4xx 는 요청마다의 문제라 Resend 는 정상으로 본다.
@Slf4j
public class ResendExpiryMailSender implements ExpiryMailSender {
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int CONFLICT = 409;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    // 같은 키 요청이 아직 처리 중 — 잠시 뒤 다시 보내면 된다.
    private static final String CONCURRENT_IDEMPOTENT = "concurrent_idempotent_requests";
    private static final Pattern ERROR_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final RestClient restClient;
    private final String from;
    private final ExpiryMailTemplate template;
    private final CircuitBreaker circuitBreaker;
    private final Duration notAttemptedDelay;

    public ResendExpiryMailSender(
            RestClient restClient,
            String from,
            ExpiryMailTemplate template,
            CircuitBreaker circuitBreaker,
            Duration notAttemptedDelay) {
        this.restClient = restClient;
        this.from = from;
        this.template = template;
        this.circuitBreaker = circuitBreaker;
        this.notAttemptedDelay = notAttemptedDelay;
    }

    @Override
    public boolean isAvailable() {
        CircuitBreaker.State state = circuitBreaker.getState();
        return state != CircuitBreaker.State.OPEN && state != CircuitBreaker.State.FORCED_OPEN;
    }

    @Override
    public MailSendOutcome send(ExpiryMail mail) {
        if (!circuitBreaker.tryAcquirePermission()) {
            return MailSendOutcome.notAttempted(LocalDateTime.now().plus(notAttemptedDelay));
        }
        long startedAt = System.nanoTime();
        Attempt attempt;
        try {
            attempt = call(mail);
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
        return attempt.outcome();
    }

    private Attempt call(ExpiryMail mail) {
        try {
            SendEmailReply reply = restClient
                    .post()
                    .uri("/emails")
                    .header("Idempotency-Key", mail.idempotencyKey())
                    .body(new SendEmailBody(
                            from, List.of(mail.to()), template.subject(mail), template.text(mail), template.html(mail)))
                    .retrieve()
                    .body(SendEmailReply.class);
            return new Attempt(MailSendOutcome.sent(reply == null ? null : reply.id()), Verdict.HEALTHY, null);
        } catch (HttpStatusCodeException e) {
            return new Attempt(classify(e, mail), verdictOf(e), e);
        } catch (RestClientException e) {
            // 타임아웃·연결 실패: Resend 가 받았는지 모르지만 같은 키로 다시 보내면 중복되지 않는다.
            return new Attempt(
                    MailSendOutcome.retryable(e.getClass().getSimpleName() + ": " + e.getMessage()),
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

    private MailSendOutcome classify(HttpStatusCodeException e, ExpiryMail mail) {
        int status = e.getStatusCode().value();
        String error = status + " " + e.getResponseBodyAsString();
        if (status == TOO_MANY_REQUESTS
                || e.getStatusCode().is5xxServerError()
                || (status == CONFLICT && error.contains(CONCURRENT_IDEMPOTENT))) {
            return MailSendOutcome.retryable(error);
        }
        if (status == UNAUTHORIZED || status == FORBIDDEN || status == CONFLICT) {
            // 키·발신 주소 설정 문제이거나, 재시도 사이 메일 내용이 바뀐 버그다. 다시 보내도 같다.
            // 응답 본문(message)에는 계정 이메일이 들어 있을 수 있어 로그에는 상태와 오류 이름만 남긴다.
            log.error(
                    "Resend 설정 또는 멱등 키 오류로 만료 알림을 보낼 수 없다 key={} status={} name={}",
                    mail.idempotencyKey(),
                    status,
                    errorName(e.getResponseBodyAsString()));
        }
        return MailSendOutcome.permanent(error);
    }

    private static String errorName(String body) {
        Matcher matcher = ERROR_NAME.matcher(body);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    // 서킷 입장에서 이번 호출이 Resend 의 건강에 대해 말해 주는 것
    private enum Verdict {
        HEALTHY,
        UNHEALTHY,
        // 429: 한도 초과라 Resend 가 아픈 건 아니지만 성공도 아니다. 기록하지 않는다.
        IGNORED
    }

    private record Attempt(MailSendOutcome outcome, Verdict verdict, Throwable cause) {}

    record SendEmailBody(String from, List<String> to, String subject, String text, String html) {}

    record SendEmailReply(String id) {}
}
