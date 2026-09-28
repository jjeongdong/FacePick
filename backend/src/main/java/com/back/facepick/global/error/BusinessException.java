package com.back.facepick.global.error;

// 추상으로 둬서 new BusinessException(...) 대신 에러마다 전용 예외 클래스를 만들게 한다.
public abstract class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
