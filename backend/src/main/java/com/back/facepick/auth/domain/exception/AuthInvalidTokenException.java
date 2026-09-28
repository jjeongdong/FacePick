package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidTokenException extends BusinessException {
    public AuthInvalidTokenException() {
        super(AuthErrorCode.AUTH_INVALID_TOKEN);
    }
}
