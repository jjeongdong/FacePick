package com.back.facepick.auth.domain;

public interface AuthTokenProvider {

    // "Bearer " 접두사를 붙여 돌려준다. 클라이언트는 그대로 Authorization 헤더에 넣는다.
    String createAccessToken(Long userId, String role);

    String createRefreshToken(Long userId);

    // refresh 토큰이 아니거나 서명·만료가 유효하지 않으면 AuthInvalidTokenException.
    Long getUserIdFromRefreshToken(String refreshToken);

    long refreshTokenTtlMillis();
}
