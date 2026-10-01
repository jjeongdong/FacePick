package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthVerificationMailRejectedException extends BusinessException {
    public AuthVerificationMailRejectedException() {
        super(AuthErrorCode.AUTH_VERIFICATION_MAIL_REJECTED);
    }
}
