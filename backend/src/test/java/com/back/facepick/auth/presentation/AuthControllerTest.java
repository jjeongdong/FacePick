package com.back.facepick.auth.presentation;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.back.facepick.auth.application.AuthCommandService;
import com.back.facepick.auth.application.dto.command.LogoutCommand;
import com.back.facepick.auth.application.dto.command.SignUpCommand;
import com.back.facepick.auth.application.dto.result.SignUpResult;
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
        given(authCommandService.signUp(new SignUpCommand("me@example.com", "password123", "민수")))
                .willReturn(new SignUpResult(1L, "Bearer access", "refresh", LocalDateTime.of(2026, 9, 1, 12, 0)));

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"me@example.com\",\"password\":\"password123\",\"nickname\":\"민수\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.accessToken").value("Bearer access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400 INVALID_INPUT")
    void signUpRejectsInvalidEmail() throws Exception {
        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"password123\",\"nickname\":\"민수\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("이메일 형식이 올바르지 않습니다."));
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
