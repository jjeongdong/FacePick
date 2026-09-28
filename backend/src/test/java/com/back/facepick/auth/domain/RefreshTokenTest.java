package com.back.facepick.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Test
    @DisplayName("원문 대신 64자리 SHA-256 해시를 저장한다")
    void storesHashInsteadOfRawToken() {
        // when
        RefreshToken refreshToken = RefreshToken.create(1L, "raw-token", EXPIRES_AT);

        // then
        assertThat(refreshToken.getTokenHash()).hasSize(64).isNotEqualTo("raw-token");
        assertThat(refreshToken.getTokenHash()).isEqualTo(RefreshToken.hash("raw-token"));
        assertThat(refreshToken.getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    @DisplayName("다른 토큰은 다른 해시가 된다")
    void differentTokensHaveDifferentHashes() {
        // when & then
        assertThat(RefreshToken.hash("token-a")).isNotEqualTo(RefreshToken.hash("token-b"));
    }
}
