package com.back.facepick.global.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class JwtAuthenticationEntryPointTest {

    @Test
    @DisplayName("인증 없이 보호된 경로에 오면 401 과 UNAUTHORIZED 본문으로 응답한다")
    void writesUnauthorized() throws Exception {
        // given
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("인증 없음"));

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("{\"code\":\"UNAUTHORIZED\",\"message\":\"인증에 실패했습니다.\"}");
    }
}
