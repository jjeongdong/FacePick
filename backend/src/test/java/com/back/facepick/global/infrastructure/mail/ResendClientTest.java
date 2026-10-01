package com.back.facepick.global.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendClientTest {

    private static final String URL = "https://api.resend.test/emails";
    private static final ResendEmail EMAIL = new ResendEmail("me@example.com", "제목", "본문", "<p>본문</p>", "key-1");

    private MockRestServiceServer server;
    private CircuitBreaker circuitBreaker;
    private ResendClient client;

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
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build());
        client = new ResendClient(builder.build(), "facepick <onboarding@resend.dev>", circuitBreaker);
    }

    private void respondWith(int status) {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\":" + status + ",\"name\":\"x\",\"message\":\"x\"}"));
    }

    private void sendTimes(int count) {
        for (int i = 0; i < count; i++) {
            client.send(EMAIL);
        }
    }

    @Test
    @DisplayName("200 - 인증·멱등 키 헤더와 발신 주소를 담아 보내고 메일 ID 를 돌려준다")
    void sendsWithHeaders() {
        // given
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer re_test_key"))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andExpect(content()
                        .json("{\"from\":\"facepick <onboarding@resend.dev>\",\"to\":[\"me@example.com\"],"
                                + "\"subject\":\"제목\",\"text\":\"본문\",\"html\":\"<p>본문</p>\"}"))
                .andRespond(withSuccess("{\"id\":\"re_abc\"}", MediaType.APPLICATION_JSON));

        // when
        ResendResult result = client.send(EMAIL);

        // then
        assertThat(result).isEqualTo(ResendResult.sent("re_abc"));
        server.verify();
    }

    @Test
    @DisplayName("멱등 키가 없으면 Idempotency-Key 헤더를 붙이지 않는다")
    void omitsIdempotencyKeyWhenNull() {
        // given
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withSuccess("{\"id\":\"re_abc\"}", MediaType.APPLICATION_JSON));

        // when
        client.send(new ResendEmail("me@example.com", "제목", "본문", "<p>본문</p>", null));

        // then
        server.verify();
    }

    @Test
    @DisplayName("HTTP 오류는 상태와 응답 본문을 담은 실패로 돌려준다")
    void httpErrorKeepsStatusAndBody() {
        // given
        respondWith(422);

        // when
        ResendResult result = client.send(EMAIL);

        // then
        assertThat(result.type()).isEqualTo(ResendResult.Type.FAILED);
        assertThat(result.status()).isEqualTo(422);
        assertThat(result.body()).contains("\"statusCode\":422");
        assertThat(result.isNetworkError()).isFalse();
    }

    @Test
    @DisplayName("타임아웃·연결 실패는 상태 없는 네트워크 실패로 돌려준다")
    void networkErrorHasNoStatus() {
        // given
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("Connection refused")));

        // when
        ResendResult timeout = client.send(EMAIL);
        ResendResult refused = client.send(EMAIL);

        // then
        assertThat(timeout.isNetworkError()).isTrue();
        assertThat(timeout.status()).isNull();
        assertThat(timeout.error()).contains("Read timed out");
        assertThat(refused.isNetworkError()).isTrue();
    }

    @Test
    @DisplayName("타임아웃 5건이면 서킷이 열리고, 다음 호출은 HTTP 요청 없이 NOT_ATTEMPTED")
    void opensAfterTimeouts() {
        // given
        for (int i = 0; i < 5; i++) {
            server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        }
        sendTimes(5);
        server.verify();
        server.reset();
        server.expect(never(), requestTo(URL));

        // when
        ResendResult result = client.send(EMAIL);

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(client.isAvailable()).isFalse();
        assertThat(result.type()).isEqualTo(ResendResult.Type.NOT_ATTEMPTED);
        server.verify();
    }

    @Test
    @DisplayName("5xx 도 서킷 실패로 센다")
    void serverErrorsOpen() {
        // given
        for (int i = 0; i < 5; i++) {
            respondWith(503);
        }

        // when
        sendTimes(5);

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("422 가 이어져도 서킷은 열리지 않는다 - 잘못된 주소는 그 요청만의 문제다")
    void clientErrorsDoNotOpen() {
        // given
        for (int i = 0; i < 10; i++) {
            respondWith(422);
        }

        // when
        sendTimes(10);

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("429 는 서킷에 기록하지 않는다")
    void rateLimitIsNotRecorded() {
        // given
        for (int i = 0; i < 10; i++) {
            respondWith(429);
        }

        // when
        sendTimes(10);

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
            server.expect(requestTo(URL)).andRespond(withSuccess("{\"id\":\"re_ok\"}", MediaType.APPLICATION_JSON));
        }

        // when
        sendTimes(2);

        // then
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("HALF_OPEN 시험 호출 2건을 다 쓰면 결과가 나오기 전 추가 호출은 NOT_ATTEMPTED")
    void halfOpenLimitsTrialCalls() {
        // given
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        circuitBreaker.tryAcquirePermission();
        circuitBreaker.tryAcquirePermission();
        server.expect(never(), requestTo(URL));

        // when
        ResendResult result = client.send(EMAIL);

        // then
        assertThat(result.type()).isEqualTo(ResendResult.Type.NOT_ATTEMPTED);
        server.verify();
    }

    @Test
    @DisplayName("예상 못 한 예외도 서킷 실패로 기록하고 그대로 던진다 - HALF_OPEN 허가가 새지 않게")
    void unexpectedExceptionIsRecorded() {
        // given
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IllegalStateException("boom");
        });

        // when & then
        assertThatThrownBy(() -> client.send(EMAIL)).isInstanceOf(IllegalStateException.class);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("Error 가 나도 서킷 실패로 기록한다 - HALF_OPEN 허가가 새면 재시작 전까지 메일이 멈춘다")
    void errorIsRecorded() {
        // given
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new StackOverflowError("boom");
        });

        // when & then
        assertThatThrownBy(() -> client.send(EMAIL)).isInstanceOf(StackOverflowError.class);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }
}
