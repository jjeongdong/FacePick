package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.MailSendOutcome;
import com.back.facepick.global.infrastructure.mail.ResendClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class ResendExpiryMailSenderTest {

    private static final ExpiryMail MAIL = new ExpiryMail(
            "album-expiry-notice-42", "me@example.com", 13L, "제주 여행", LocalDateTime.of(2026, 10, 7, 15, 0));

    private static final Duration OPEN_WAIT = Duration.ofSeconds(60);

    private MockRestServiceServer server;
    private CircuitBreaker circuitBreaker;
    private ResendExpiryMailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.resend.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer re_test_key");
        server = MockRestServiceServer.bindTo(builder).build();
        // 운영 설정과 같은 값. 자동 전환은 테스트에서 직접 전환하므로 끈다.
        circuitBreaker = CircuitBreaker.of(
                "resend-test",
                CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(3))
                        .slowCallRateThreshold(50)
                        .waitDurationInOpenState(OPEN_WAIT)
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build());
        sender = new ResendExpiryMailSender(
                new ResendClient(builder.build(), "facepick <onboarding@resend.dev>", circuitBreaker),
                new ExpiryMailTemplate("http://localhost:5173"),
                OPEN_WAIT);
    }

    private void timeouts(int count) {
        for (int i = 0; i < count; i++) {
            server.expect(requestTo("https://api.resend.test/emails"))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));
        }
    }

    private void respondWith(int status, String body) {
        server.expect(requestTo("https://api.resend.test/emails"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
    }

    @Test
    @DisplayName("200 - 인증·멱등 키 헤더와 메일 본문을 보내고 Resend 메일 ID 를 돌려준다")
    void sendsWithHeadersAndReturnsId() {
        // given
        server.expect(requestTo("https://api.resend.test/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer re_test_key"))
                .andExpect(header("Idempotency-Key", "album-expiry-notice-42"))
                .andExpect(content()
                        .json("{\"from\":\"facepick <onboarding@resend.dev>\",\"to\":[\"me@example.com\"],"
                                + "\"subject\":\"[facepick] '제주 여행' 앨범이 10월 7일에 삭제돼요\"}"))
                .andRespond(withSuccess("{\"id\":\"re_abc\"}", MediaType.APPLICATION_JSON));

        // when
        MailSendOutcome outcome = sender.send(MAIL);

        // then
        assertThat(outcome).isEqualTo(MailSendOutcome.sent("re_abc"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503})
    @DisplayName("429·5xx 는 일시 실패")
    void retryableStatuses(int status) {
        // given
        respondWith(status, "{\"statusCode\":" + status + ",\"name\":\"x\",\"message\":\"x\"}");

        // when & then
        assertThat(sender.send(MAIL).type()).isEqualTo(MailSendOutcome.Type.RETRYABLE);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 422})
    @DisplayName("그 밖의 4xx 는 영구 실패")
    void permanentStatuses(int status) {
        // given
        respondWith(status, "{\"statusCode\":" + status + ",\"name\":\"validation_error\",\"message\":\"x\"}");

        // when & then
        assertThat(sender.send(MAIL).type()).isEqualTo(MailSendOutcome.Type.PERMANENT);
    }

    @Test
    @DisplayName("409 concurrent_idempotent_requests 는 같은 키 요청이 처리 중이라 일시 실패")
    void concurrentIdempotentIsRetryable() {
        // given
        respondWith(409, "{\"statusCode\":409,\"name\":\"concurrent_idempotent_requests\",\"message\":\"x\"}");

        // when & then
        assertThat(sender.send(MAIL).type()).isEqualTo(MailSendOutcome.Type.RETRYABLE);
    }

    @Test
    @DisplayName("409 invalid_idempotent_request 는 재시도 사이 내용이 바뀐 것이라 영구 실패")
    void invalidIdempotentIsPermanent() {
        // given
        respondWith(409, "{\"statusCode\":409,\"name\":\"invalid_idempotent_request\",\"message\":\"x\"}");

        // when
        MailSendOutcome outcome = sender.send(MAIL);

        // then
        assertThat(outcome.type()).isEqualTo(MailSendOutcome.Type.PERMANENT);
        assertThat(outcome.error()).contains("409").contains("invalid_idempotent_request");
    }

    @Test
    @DisplayName("읽기 타임아웃은 일시 실패")
    void readTimeoutIsRetryable() {
        // given
        server.expect(requestTo("https://api.resend.test/emails"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        // when & then
        assertThat(sender.send(MAIL).type()).isEqualTo(MailSendOutcome.Type.RETRYABLE);
    }

    @Test
    @DisplayName("연결 실패는 일시 실패")
    void connectionFailureIsRetryable() {
        // given
        server.expect(requestTo("https://api.resend.test/emails"))
                .andRespond(withException(new ConnectException("Connection refused")));

        // when & then
        assertThat(sender.send(MAIL).type()).isEqualTo(MailSendOutcome.Type.RETRYABLE);
    }

    @Test
    @DisplayName("설정 오류 로그에는 상태와 오류 이름만 남기고, 이메일이 든 응답 본문은 남기지 않는다")
    void doesNotLogResponseBody(CapturedOutput output) {
        // given
        respondWith(
                403,
                "{\"statusCode\":403,\"name\":\"validation_error\","
                        + "\"message\":\"You can only send testing emails to your own email address (owner@example.com).\"}");

        // when
        sender.send(MAIL);

        // then
        assertThat(output).contains("403").contains("validation_error").doesNotContain("owner@example.com");
    }

    @Test
    @DisplayName("타임아웃 5건이면 서킷이 열리고, 다음 발송은 HTTP 요청 없이 보내지 않은 결과가 된다")
    void opensAfterTimeouts() {
        // given
        timeouts(5);
        for (int i = 0; i < 5; i++) {
            sender.send(MAIL);
        }
        server.verify();
        server.reset();
        server.expect(never(), requestTo("https://api.resend.test/emails"));
        LocalDateTime before = LocalDateTime.now();

        // when
        MailSendOutcome outcome = sender.send(MAIL);

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(sender.isAvailable()).isFalse();
        assertThat(outcome.type()).isEqualTo(MailSendOutcome.Type.NOT_ATTEMPTED);
        assertThat(outcome.retryAt()).isAfterOrEqualTo(before.plus(OPEN_WAIT));
        server.verify();
    }

    @Test
    @DisplayName("5xx 도 서킷 실패로 센다")
    void serverErrorsOpen() {
        // given
        for (int i = 0; i < 5; i++) {
            respondWith(503, "{\"statusCode\":503,\"name\":\"x\",\"message\":\"x\"}");
        }

        // when
        for (int i = 0; i < 5; i++) {
            sender.send(MAIL);
        }

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("422 가 이어져도 서킷은 열리지 않는다 - 잘못된 주소는 그 요청만의 문제다")
    void clientErrorsDoNotOpen() {
        // given
        for (int i = 0; i < 10; i++) {
            respondWith(422, "{\"statusCode\":422,\"name\":\"validation_error\",\"message\":\"x\"}");
        }

        // when
        for (int i = 0; i < 10; i++) {
            sender.send(MAIL);
        }

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(sender.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("429 는 서킷에 기록하지 않는다 - 한도는 기존 백오프가 맡는다")
    void rateLimitIsNotRecorded() {
        // given
        for (int i = 0; i < 10; i++) {
            respondWith(429, "{\"statusCode\":429,\"name\":\"rate_limit_exceeded\",\"message\":\"x\"}");
        }

        // when
        for (int i = 0; i < 10; i++) {
            sender.send(MAIL);
        }

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    @DisplayName("HALF_OPEN 에서 시험 호출 2건이 성공하면 다시 닫힌다")
    void halfOpenClosesAfterTwoSuccesses() {
        // given
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo("https://api.resend.test/emails"))
                    .andRespond(withSuccess("{\"id\":\"re_ok\"}", MediaType.APPLICATION_JSON));
        }

        // when
        sender.send(MAIL);
        sender.send(MAIL);

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("HALF_OPEN 시험 호출 2건을 다 쓰면 결과가 나오기 전 추가 발송은 보내지 않은 결과가 된다")
    void halfOpenLimitsTrialCalls() {
        // given
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        circuitBreaker.tryAcquirePermission();
        circuitBreaker.tryAcquirePermission();
        server.expect(never(), requestTo("https://api.resend.test/emails"));

        // when
        MailSendOutcome outcome = sender.send(MAIL);

        // then
        assertThat(outcome.type()).isEqualTo(MailSendOutcome.Type.NOT_ATTEMPTED);
        server.verify();
    }

    @Test
    @DisplayName("예상 못 한 예외도 서킷 실패로 기록하고 그대로 던진다 - HALF_OPEN 허가가 새지 않게")
    void unexpectedExceptionIsRecorded() {
        // given
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        server.expect(requestTo("https://api.resend.test/emails")).andRespond(request -> {
            throw new IllegalStateException("boom");
        });

        // when & then
        assertThatThrownBy(() -> sender.send(MAIL)).isInstanceOf(IllegalStateException.class);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("Error 가 나도 서킷 실패로 기록한다 - HALF_OPEN 허가가 새면 재시작 전까지 메일이 멈춘다")
    void errorIsRecorded() {
        // given
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        server.expect(requestTo("https://api.resend.test/emails")).andRespond(request -> {
            throw new StackOverflowError("boom");
        });

        // when & then
        assertThatThrownBy(() -> sender.send(MAIL)).isInstanceOf(StackOverflowError.class);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }
}
