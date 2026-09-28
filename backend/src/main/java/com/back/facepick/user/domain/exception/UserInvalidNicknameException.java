package com.back.facepick.user.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.user.domain.UserErrorCode;

public class UserInvalidNicknameException extends BusinessException {
    public UserInvalidNicknameException() {
        super(UserErrorCode.USER_INVALID_NICKNAME);
    }
}
