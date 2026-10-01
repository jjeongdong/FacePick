package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthVerificationResendTooSoonException extends BusinessException {
    public AuthVerificationResendTooSoonException() {
        super(AuthErrorCode.AUTH_VERIFICATION_RESEND_TOO_SOON);
    }
}
