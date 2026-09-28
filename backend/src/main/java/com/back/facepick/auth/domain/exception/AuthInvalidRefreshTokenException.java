package com.back.facepick.auth.domain.exception;

import com.back.facepick.auth.domain.AuthErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AuthInvalidRefreshTokenException extends BusinessException {
    public AuthInvalidRefreshTokenException() {
        super(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
    }
}
