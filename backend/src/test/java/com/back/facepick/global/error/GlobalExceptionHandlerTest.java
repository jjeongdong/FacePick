package com.back.facepick.global.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("전용 비즈니스 예외는 ErrorType 에 맞는 상태와 자기 코드로 응답한다")
    void businessExceptionUsesItsCodeAndMappedStatus() throws Exception {
        assertError(get("/test/not-found"), 404, "TEST_NOT_FOUND", "테스트 대상이 없습니다.");
    }

    @Test
    @DisplayName("외부 장애 비즈니스 예외는 502 로 응답한다")
    void externalBusinessExceptionIsBadGateway() throws Exception {
        assertError(get("/test/external"), 502, "TEST_EXTERNAL", "외부 서비스 장애입니다.");
    }

    @Test
    @DisplayName("@Valid 실패는 400 INVALID_INPUT 과 첫 필드 에러 메시지로 응답한다")
    void validationFailureUsesFirstFieldMessage() throws Exception {
        RequestBuilder request =
                post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}");

        assertError(request, 400, "INVALID_INPUT", "이름은 필수입니다.");
    }

    @Test
    @DisplayName("JSON 형식 오류는 400 INVALID_INPUT 으로 응답한다")
    void malformedBodyIsInvalidInput() throws Exception {
        RequestBuilder request =
                post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{");

        assertError(request, 400, "INVALID_INPUT", "요청 본문 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("필수 파라미터 누락은 400 INVALID_INPUT 으로 응답한다")
    void missingParameterIsInvalidInput() throws Exception {
        assertError(get("/test/param"), 400, "INVALID_INPUT", "입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("파라미터 타입 불일치는 400 INVALID_INPUT 으로 응답한다")
    void typeMismatchIsInvalidInput() throws Exception {
        assertError(get("/test/param").param("page", "abc"), 400, "INVALID_INPUT", "입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("없는 리소스는 404 NOT_FOUND 로 응답한다")
    void noResourceIsNotFound() throws Exception {
        assertError(get("/test/no-resource"), 404, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED 로 응답한다")
    void unsupportedMethodIsMethodNotAllowed() throws Exception {
        assertError(delete("/test/param"), 405, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다.");
    }

    @Test
    @DisplayName("업로드 용량 초과는 413 INVALID_INPUT 으로 응답한다")
    void maxUploadSizeExceededIsPayloadTooLarge() throws Exception {
        assertError(get("/test/too-large"), 413, "INVALID_INPUT", "입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type 은 415 INVALID_INPUT 으로 응답한다")
    void unsupportedMediaTypeIsInvalidInput() throws Exception {
        RequestBuilder request =
                post("/test/body").contentType(MediaType.TEXT_PLAIN).content("name");

        assertError(request, 415, "INVALID_INPUT", "입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("권한 거부는 403 FORBIDDEN 으로 응답한다")
    void accessDeniedIsForbidden() throws Exception {
        assertError(get("/test/denied"), 403, "FORBIDDEN", "접근 권한이 없습니다.");
    }

    @Test
    @DisplayName("처리되지 않은 예외는 500 INTERNAL_SERVER_ERROR 로 응답한다")
    void unexpectedExceptionIsInternalServerError() throws Exception {
        assertError(get("/test/unexpected"), 500, "INTERNAL_SERVER_ERROR", "서버 오류입니다.");
    }

    @Test
    @DisplayName("서버 원인 비즈니스 예외만 ERROR 로그를 남긴다")
    void logsOnlyServerFaultBusinessExceptions(CapturedOutput output) throws Exception {
        // when
        mockMvc.perform(get("/test/not-found"));
        mockMvc.perform(get("/test/external"));

        // then
        assertThat(output.getOut()).contains("TEST_EXTERNAL").doesNotContain("TEST_NOT_FOUND");
    }

    private void assertError(RequestBuilder request, int status, String code, String message) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
        JsonNode body = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(body.get("code").asString()).isEqualTo(code);
        assertThat(body.get("message").asString()).isEqualTo(message);
        assertThat(body.size()).isEqualTo(2);
    }

    enum TestErrorCode implements ErrorCode {
        TEST_NOT_FOUND(ErrorType.NOT_FOUND, "테스트 대상이 없습니다."),
        TEST_EXTERNAL(ErrorType.EXTERNAL, "외부 서비스 장애입니다.");

        private final ErrorType type;
        private final String message;

        TestErrorCode(ErrorType type, String message) {
            this.type = type;
            this.message = message;
        }

        @Override
        public ErrorType type() {
            return type;
        }

        @Override
        public String message() {
            return message;
        }
    }

    static class TestNotFoundException extends BusinessException {
        TestNotFoundException() {
            super(TestErrorCode.TEST_NOT_FOUND);
        }
    }

    static class TestExternalException extends BusinessException {
        TestExternalException() {
            super(TestErrorCode.TEST_EXTERNAL);
        }
    }

    record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {}

    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new TestNotFoundException();
        }

        @GetMapping("/test/external")
        void external() {
            throw new TestExternalException();
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody TestRequest request) {}

        @GetMapping("/test/param")
        void param(@RequestParam Long page) {}

        @GetMapping("/test/no-resource")
        void noResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing", "missing");
        }

        @GetMapping("/test/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(1024);
        }

        @GetMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("관리자 전용");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("예상하지 못한 실패");
        }
    }
}
