package com.back.facepick.global.error;

// 특정 BC 에 속하지 않는 요청 형식 오류(허용되지 않은 웹소켓 목적지 등)용.
public class InvalidInputException extends BusinessException {
    public InvalidInputException() {
        super(GlobalErrorCode.INVALID_INPUT);
    }
}
