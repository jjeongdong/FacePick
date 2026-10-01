package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthVerificationMailUnavailableException extends BusinessException {
    public AuthVerificationMailUnavailableException() {
        super(AuthErrorCode.AUTH_VERIFICATION_MAIL_UNAVAILABLE);
    }
}
