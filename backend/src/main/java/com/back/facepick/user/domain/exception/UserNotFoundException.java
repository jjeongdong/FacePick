package com.back.facepick.user.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.user.domain.UserErrorCode;

public class UserNotFoundException extends BusinessException {
    public UserNotFoundException() {
        super(UserErrorCode.USER_NOT_FOUND);
    }
}
