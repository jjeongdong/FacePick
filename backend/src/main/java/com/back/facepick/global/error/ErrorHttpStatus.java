package com.back.facepick.global.error;

import org.springframework.http.HttpStatus;

// ErrorType 을 HTTP 로 옮기는 유일한 지점. HTTP 경계(예외 핸들러, 보안 필터 핸들러)에서만 쓴다.
public final class ErrorHttpStatus {

    private ErrorHttpStatus() {}

    public static HttpStatus of(ErrorType type) {
        return switch (type) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case EXTERNAL -> HttpStatus.BAD_GATEWAY;
        };
    }
}
