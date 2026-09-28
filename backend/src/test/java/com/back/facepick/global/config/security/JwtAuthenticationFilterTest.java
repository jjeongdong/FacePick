package com.back.facepick.global.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String VALID_TOKEN = "valid";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(token -> {
        if (VALID_TOKEN.equals(token)) {
            return new AuthenticatedUser(7L, "ROLE_USER");
        }
        throw new IllegalArgumentException("유효하지 않은 토큰");
    });

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("올바른 Bearer 토큰이면 사용자 ID 로 인증한다")
    void authenticatesValidToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(7L);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("잘못된 토큰이면 인증 없이 다음 필터로 넘긴다")
    void skipsInvalidToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer broken");
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 인증 없이 다음 필터로 넘긴다")
    void skipsMissingHeader() throws Exception {
        // given
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }
}
