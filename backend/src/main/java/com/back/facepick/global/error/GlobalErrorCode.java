package com.back.facepick.global.error;

public enum GlobalErrorCode implements ErrorCode {
    INVALID_INPUT(ErrorType.INVALID, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."),
    FORBIDDEN(ErrorType.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(ErrorType.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    // 405 는 HTTP 전용이라 ErrorType 을 늘리지 않고 GlobalExceptionHandler 가 상태를 직접 정한다.
    METHOD_NOT_ALLOWED(ErrorType.INVALID, "지원하지 않는 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR(ErrorType.INTERNAL, "서버 오류입니다.");

    private final ErrorType type;
    private final String message;

    GlobalErrorCode(ErrorType type, String message) {
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
