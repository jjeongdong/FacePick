package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthVerificationCodeMismatchException extends BusinessException {
    public AuthVerificationCodeMismatchException() {
        super(AuthErrorCode.AUTH_VERIFICATION_CODE_MISMATCH);
    }
}
