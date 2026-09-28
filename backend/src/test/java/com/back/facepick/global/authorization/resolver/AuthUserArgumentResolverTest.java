package com.back.facepick.global.authorization.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.global.error.UnauthorizedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthUserArgumentResolverTest {

    private final AuthUserArgumentResolver resolver = new AuthUserArgumentResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 정보의 사용자 ID 를 Long 으로 넘긴다")
    void resolvesUserId() {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));

        // when
        Object userId = resolver.resolveArgument(null, null, null, null);

        // then
        assertThat(userId).isEqualTo(7L);
    }

    @Test
    @DisplayName("인증 정보가 없으면 UnauthorizedException 을 던진다")
    void throwsWhenNoAuthentication() {
        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("익명 사용자면 UnauthorizedException 을 던진다")
    void throwsWhenAnonymous() {
        // given
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(UnauthorizedException.class);
    }
}
