package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.MailSendOutcome;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
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

    private MockRestServiceServer server;
    private ResendExpiryMailSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.resend.test")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer re_test_key");
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new ResendExpiryMailSender(
                builder.build(), "facepick <onboarding@resend.dev>", new ExpiryMailTemplate("http://localhost:5173"));
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
}
