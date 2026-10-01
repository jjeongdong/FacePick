package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthVerificationAttemptsExceededException extends BusinessException {
    public AuthVerificationAttemptsExceededException() {
        super(AuthErrorCode.AUTH_VERIFICATION_ATTEMPTS_EXCEEDED);
    }
}
