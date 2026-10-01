package com.back.facepick.auth.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.facepick.auth.application.AuthCommandService;
import com.back.facepick.auth.application.dto.command.EmailVerificationSendCommand;
import com.back.facepick.auth.application.dto.command.LogoutCommand;
import com.back.facepick.auth.application.dto.command.SignUpCommand;
import com.back.facepick.auth.application.dto.result.SignUpResult;
import com.back.facepick.auth.domain.exception.AuthVerificationMailUnavailableException;
import com.back.facepick.auth.domain.exception.AuthVerificationResendTooSoonException;
import com.back.facepick.global.error.GlobalExceptionHandler;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthCommandService authCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/signup 은 201 과 토큰")
    void signUp() throws Exception {
        // given
        given(authCommandService.signUp(new SignUpCommand("me@example.com", "password123", "민수", "012345")))
                .willReturn(new SignUpResult(1L, "Bearer access", "refresh", LocalDateTime.of(2026, 9, 1, 12, 0)));

        // when & then
        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"me@example.com\",\"password\":\"password123\",\"nickname\":\"민수\",\"code\":\"012345\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.accessToken").value("Bearer access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400 INVALID_INPUT")
    void signUpRejectsInvalidEmail() throws Exception {
        // when & then
        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"not-an-email\",\"password\":\"password123\",\"nickname\":\"민수\",\"code\":\"012345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("이메일 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("인증 코드가 6자리 숫자가 아니면 400 INVALID_INPUT")
    void signUpRejectsMalformedCode() throws Exception {
        // when & then
        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"me@example.com\",\"password\":\"password123\",\"nickname\":\"민수\",\"code\":\"12a45\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("인증 코드는 6자리 숫자입니다."));
    }

    @Test
    @DisplayName("POST /api/auth/email-verifications 는 204")
    void sendEmailVerification() throws Exception {
        // when & then
        mockMvc.perform(post("/api/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"me@example.com\"}"))
                .andExpect(status().isNoContent());
        then(authCommandService).should().sendEmailVerification(new EmailVerificationSendCommand("me@example.com"));
    }

    @Test
    @DisplayName("60초 안에 다시 요청하면 429")
    void sendEmailVerificationTooSoon() throws Exception {
        // given
        willThrow(new AuthVerificationResendTooSoonException())
                .given(authCommandService)
                .sendEmailVerification(new EmailVerificationSendCommand("me@example.com"));

        // when & then
        mockMvc.perform(post("/api/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"me@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_RESEND_TOO_SOON"));
    }

    @Test
    @DisplayName("메일 서비스를 쓸 수 없으면 502")
    void sendEmailVerificationUnavailable() throws Exception {
        // given
        willThrow(new AuthVerificationMailUnavailableException())
                .given(authCommandService)
                .sendEmailVerification(new EmailVerificationSendCommand("me@example.com"));

        // when & then
        mockMvc.perform(post("/api/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"me@example.com\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_MAIL_UNAVAILABLE"));
    }

    @Test
    @DisplayName("POST /api/auth/logout 은 204")
    void logout() throws Exception {
        // when & then
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh\"}"))
                .andExpect(status().isNoContent());
        then(authCommandService).should().logout(new LogoutCommand("refresh"));
    }
}
