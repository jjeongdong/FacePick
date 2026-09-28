package com.back.facepick.global.error;

// 특정 BC 에 속하지 않는 인증 실패(요청에 로그인 정보가 없거나 형식이 잘못됨)용.
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException() {
        super(GlobalErrorCode.UNAUTHORIZED);
    }
}
