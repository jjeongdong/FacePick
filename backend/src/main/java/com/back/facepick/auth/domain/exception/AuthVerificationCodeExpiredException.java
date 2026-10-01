package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthVerificationCodeExpiredException extends BusinessException {
    public AuthVerificationCodeExpiredException() {
        super(AuthErrorCode.AUTH_VERIFICATION_CODE_EXPIRED);
    }
}
