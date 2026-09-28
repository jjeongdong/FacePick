package com.back.facepick.auth.domain;

public interface RefreshTokenRepository {
    RefreshToken save(RefreshToken refreshToken);

    boolean existsByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}
