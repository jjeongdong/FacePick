package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.auth.domain.exception.AuthInvalidTokenException;
import com.back.facepick.global.config.security.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-must-be-32-bytes!!";
    private static final long ONE_MINUTE = 60_000L;

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, ONE_MINUTE, ONE_MINUTE);

    @Test
    @DisplayName("액세스 토큰은 Bearer 접두사가 붙고, 사용자 ID 와 권한으로 검증된다")
    void verifiesAccessToken() {
        // when
        String accessToken = provider.createAccessToken(7L, "ROLE_USER");

        // then
        assertThat(accessToken).startsWith("Bearer ");
        assertThat(provider.verify(accessToken)).isEqualTo(new AuthenticatedUser(7L, "ROLE_USER"));
    }

    @Test
    @DisplayName("리프레시 토큰에서 사용자 ID 를 꺼낸다")
    void readsUserIdFromRefreshToken() {
        // when
        String refreshToken = provider.createRefreshToken(7L);

        // then
        assertThat(provider.getUserIdFromRefreshToken(refreshToken)).isEqualTo(7L);
    }

    @Test
    @DisplayName("같은 사용자에게 연달아 발급한 리프레시 토큰은 서로 다르다")
    void refreshTokensAreUnique() {
        // when
        String first = provider.createRefreshToken(7L);
        String second = provider.createRefreshToken(7L);

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Nested
    @DisplayName("거부하는 토큰")
    class Rejects {

        @Test
        @DisplayName("리프레시 토큰으로는 API 인증을 할 수 없다")
        void refreshTokenCannotAuthenticate() {
            // given
            String refreshToken = provider.createRefreshToken(7L);

            // when & then
            assertThatThrownBy(() -> provider.verify(refreshToken)).isInstanceOf(AuthInvalidTokenException.class);
        }

        @Test
        @DisplayName("액세스 토큰으로는 재발급을 받을 수 없다")
        void accessTokenCannotReissue() {
            // given
            String accessToken = provider.createAccessToken(7L, "ROLE_USER");

            // when & then
            assertThatThrownBy(() -> provider.getUserIdFromRefreshToken(accessToken))
                    .isInstanceOf(AuthInvalidTokenException.class);
        }

        @Test
        @DisplayName("다른 키로 서명된 토큰은 거부한다")
        void rejectsForeignSignature() {
            // given
            JwtTokenProvider other =
                    new JwtTokenProvider("another-secret-key-for-jwt-32-bytes-long!!", ONE_MINUTE, ONE_MINUTE);
            String accessToken = other.createAccessToken(7L, "ROLE_USER");

            // when & then
            assertThatThrownBy(() -> provider.verify(accessToken)).isInstanceOf(AuthInvalidTokenException.class);
        }

        @Test
        @DisplayName("만료된 토큰은 거부한다")
        void rejectsExpiredToken() {
            // given
            JwtTokenProvider expired = new JwtTokenProvider(SECRET, -ONE_MINUTE, -ONE_MINUTE);
            String accessToken = expired.createAccessToken(7L, "ROLE_USER");

            // when & then
            assertThatThrownBy(() -> provider.verify(accessToken)).isInstanceOf(AuthInvalidTokenException.class);
        }

        @Test
        @DisplayName("비어 있는 토큰은 거부한다")
        void rejectsBlankToken() {
            // when & then
            assertThatThrownBy(() -> provider.getUserIdFromRefreshToken(""))
                    .isInstanceOf(AuthInvalidTokenException.class);
        }
    }
}
