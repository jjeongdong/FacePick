package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthEmailAlreadyExistsException extends BusinessException {
    public AuthEmailAlreadyExistsException() {
        super(AuthErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
    }
}
