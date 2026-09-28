package com.back.facepick.user.domain;

import com.back.facepick.global.error.ErrorCode;
import com.back.facepick.global.error.ErrorType;

public enum UserErrorCode implements ErrorCode {
    USER_NOT_FOUND(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다."),
    USER_INVALID_NICKNAME(ErrorType.INVALID, "닉네임은 1~20자여야 합니다.");

    private final ErrorType type;
    private final String message;

    UserErrorCode(ErrorType type, String message) {
        this.type = type;
        this.message = message;
    }

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String message() {
        return message;
    }
}
