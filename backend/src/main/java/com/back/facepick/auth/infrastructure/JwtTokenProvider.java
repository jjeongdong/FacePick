package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.AuthTokenProvider;
import com.back.facepick.auth.domain.exception.AuthInvalidTokenException;
import com.back.facepick.global.config.security.AccessTokenVerifier;
import com.back.facepick.global.config.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider implements AuthTokenProvider, AccessTokenVerifier {
    private static final String BEARER = "Bearer ";
    private static final String ACCESS_TOKEN_SUBJECT = "AccessToken";
    private static final String REFRESH_TOKEN_SUBJECT = "RefreshToken";
    private static final String ID_CLAIM = "id";
    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret-key}") String secretKey,
            @Value("${jwt.access-expiration-millis}") long accessTokenExpirationMillis,
            @Value("${jwt.refresh-expiration-millis}") long refreshTokenExpirationMillis) {
        // 32바이트보다 짧은 키면 여기서 WeakKeyException 이 나 앱이 뜨지 않는다.
        this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMillis = accessTokenExpirationMillis;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
    }

    @Override
    public String createAccessToken(Long userId, String role) {
        Date now = new Date();
        return BEARER
                + Jwts.builder()
                        .subject(ACCESS_TOKEN_SUBJECT)
                        .claim(ID_CLAIM, userId)
                        .claim(ROLE_CLAIM, role)
                        .issuedAt(now)
                        .expiration(new Date(now.getTime() + accessTokenExpirationMillis))
                        .signWith(signingKey)
                        .compact();
    }

    @Override
    public String createRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                // 발급 시각이 초 단위라 같은 초에 두 번 로그인하면 토큰이 같아져 token_hash 유니크 제약에 걸린다. jti 로 구분한다.
                .id(UUID.randomUUID().toString())
                .subject(REFRESH_TOKEN_SUBJECT)
                .claim(ID_CLAIM, userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpirationMillis))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public Long getUserIdFromRefreshToken(String refreshToken) {
        return parseClaims(refreshToken, REFRESH_TOKEN_SUBJECT).get(ID_CLAIM, Long.class);
    }

    @Override
    public AuthenticatedUser verify(String token) {
        Claims claims = parseClaims(token, ACCESS_TOKEN_SUBJECT);
        return new AuthenticatedUser(claims.get(ID_CLAIM, Long.class), claims.get(ROLE_CLAIM, String.class));
    }

    @Override
    public long refreshTokenTtlMillis() {
        return refreshTokenExpirationMillis;
    }

    // access·refresh 가 같은 키로 서명되므로 subject 로 용도를 구분한다.
    // refresh 토큰으로 API 를 호출하거나 access 토큰으로 재발급받는 것을 막는다.
    private Claims parseClaims(String token, String expectedSubject) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(removeBearer(token))
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthInvalidTokenException();
        }
        if (!expectedSubject.equals(claims.getSubject())) {
            throw new AuthInvalidTokenException();
        }
        return claims;
    }

    private String removeBearer(String token) {
        if (token != null && token.startsWith(BEARER)) {
            return token.substring(BEARER.length());
        }
        return token;
    }
}
