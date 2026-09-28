package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.auth.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return refreshTokenJpaRepository.save(refreshToken);
    }

    @Override
    public boolean existsByTokenHash(String tokenHash) {
        return refreshTokenJpaRepository.existsByTokenHash(tokenHash);
    }

    @Override
    public void deleteByTokenHash(String tokenHash) {
        refreshTokenJpaRepository.deleteByTokenHash(tokenHash);
    }
}
