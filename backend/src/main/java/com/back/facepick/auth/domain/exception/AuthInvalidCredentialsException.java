package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidCredentialsException extends BusinessException {
    public AuthInvalidCredentialsException() {
        super(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
    }
}
