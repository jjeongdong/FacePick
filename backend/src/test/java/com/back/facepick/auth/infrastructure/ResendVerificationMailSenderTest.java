package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.back.facepick.auth.domain.exception.AuthVerificationMailRejectedException;
import com.back.facepick.auth.domain.exception.AuthVerificationMailUnavailableException;
import com.back.facepick.global.infrastructure.mail.ResendClient;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class ResendVerificationMailSenderTest {

    private static final String URL = "https://api.resend.test/emails";
    private static final int MAX_CONCURRENT_CALLS = 5;

    private MockRestServiceServer server;
    private CircuitBreaker circuitBreaker;
    private Bulkhead bulkhead;
    private ResendVerificationMailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.test");
        server = MockRestServiceServer.bindTo(builder).build();
        circuitBreaker = CircuitBreaker.ofDefaults("resend-test");
        bulkhead = Bulkhead.of(
                "verification-test",
                BulkheadConfig.custom()
                        .maxConcurrentCalls(MAX_CONCURRENT_CALLS)
                        .maxWaitDuration(Duration.ZERO)
                        .build());
        sender = new ResendVerificationMailSender(
                new ResendClient(builder.build(), "facepick <onboarding@resend.dev>", circuitBreaker),
                bulkhead,
                new VerificationMailTemplate());
    }

    private void respondWith(int status) {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"statusCode\":" + status + ",\"name\":\"validation_error\","
                                + "\"message\":\"only to owner@example.com\"}"));
    }

    @Test
    @DisplayName("200 - 코드를 담아 멱등 키 없이 보내고 Bulkhead 자리를 돌려준다")
    void sendsCode() {
        // given
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(content().json("{\"to\":[\"me@example.com\"],\"subject\":\"[facepick] 인증 코드 012345\"}"))
                .andRespond(withSuccess("{\"id\":\"re_abc\"}", MediaType.APPLICATION_JSON));

        // when & then
        assertThatCode(() -> sender.send("me@example.com", "012345")).doesNotThrowAnyException();
        server.verify();
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(MAX_CONCURRENT_CALLS);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 422})
    @DisplayName("Resend 가 주소를 거절하면 AuthVerificationMailRejectedException")
    void rejectedAddress(int status) {
        // given
        respondWith(status);

        // when & then
        assertThatThrownBy(() -> sender.send("me@example.com", "012345"))
                .isInstanceOf(AuthVerificationMailRejectedException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    @DisplayName("설정 오류·한도·서버 오류는 AuthVerificationMailUnavailableException")
    void unavailableStatuses(int status) {
        // given
        respondWith(status);

        // when & then
        assertThatThrownBy(() -> sender.send("me@example.com", "012345"))
                .isInstanceOf(AuthVerificationMailUnavailableException.class);
    }

    @Test
    @DisplayName("타임아웃은 AuthVerificationMailUnavailableException 이고 Bulkhead 자리를 돌려준다")
    void timeoutIsUnavailable() {
        // given
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        // when & then
        assertThatThrownBy(() -> sender.send("me@example.com", "012345"))
                .isInstanceOf(AuthVerificationMailUnavailableException.class);
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(MAX_CONCURRENT_CALLS);
    }

    @Test
    @DisplayName("예상 못 한 예외가 나도 그대로 던지고 Bulkhead 자리를 돌려준다")
    void releasesPermissionOnUnexpectedException() {
        // given
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IllegalStateException("boom");
        });

        // when & then
        assertThatThrownBy(() -> sender.send("me@example.com", "012345")).isInstanceOf(IllegalStateException.class);
        assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isEqualTo(MAX_CONCURRENT_CALLS);
    }

    @Test
    @DisplayName("서킷이 열려 있으면 HTTP 요청 없이 AuthVerificationMailUnavailableException")
    void circuitOpen() {
        // given
        circuitBreaker.transitionToOpenState();
        server.expect(never(), requestTo(URL));

        // when & then
        assertThatThrownBy(() -> sender.send("me@example.com", "012345"))
                .isInstanceOf(AuthVerificationMailUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("동시 호출 자리가 없으면 기다리지 않고 HTTP 요청 없이 AuthVerificationMailUnavailableException")
    void bulkheadFull() {
        // given
        for (int i = 0; i < MAX_CONCURRENT_CALLS; i++) {
            bulkhead.tryAcquirePermission();
        }
        server.expect(never(), requestTo(URL));

        // when & then
        assertThatThrownBy(() -> sender.send("me@example.com", "012345"))
                .isInstanceOf(AuthVerificationMailUnavailableException.class);
        server.verify();
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isZero();
    }

    @Test
    @DisplayName("설정 오류 로그에는 상태와 오류 이름만 남기고 이메일이 든 응답 본문은 남기지 않는다")
    void doesNotLogResponseBody(CapturedOutput output) {
        // given
        respondWith(403);

        // when & then
        assertThatThrownBy(() -> sender.send("me@example.com", "012345"))
                .isInstanceOf(AuthVerificationMailUnavailableException.class);
        assertThat(output)
                .contains("403")
                .contains("validation_error")
                .doesNotContain("owner@example.com")
                .doesNotContain("me@example.com");
    }
}
