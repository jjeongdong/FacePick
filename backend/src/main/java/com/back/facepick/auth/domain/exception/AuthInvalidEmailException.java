package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidEmailException extends BusinessException {
    public AuthInvalidEmailException() {
        super(AuthErrorCode.AUTH_INVALID_EMAIL);
    }
}
