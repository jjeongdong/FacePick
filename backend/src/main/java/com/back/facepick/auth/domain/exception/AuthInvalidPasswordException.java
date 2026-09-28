package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidPasswordException extends BusinessException {
    public AuthInvalidPasswordException() {
        super(AuthErrorCode.AUTH_INVALID_PASSWORD);
    }
}
