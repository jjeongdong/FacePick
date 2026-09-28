package com.back.facepick.global.config.security;

// 보안 필터가 인증 BC 의 구현을 모르게 하려고 둔 포트. global 은 BC 에 의존하지 않는다.
public interface AccessTokenVerifier {

    // 서명·만료·용도 검증에 실패하면 예외를 던진다. 호출 측(필터)이 예외를 잡아 인증 없음으로 처리한다.
    AuthenticatedUser verify(String token);
}
