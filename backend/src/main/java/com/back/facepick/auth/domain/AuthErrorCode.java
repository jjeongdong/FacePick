package com.back.facepick.auth.domain;

import com.back.facepick.global.error.ErrorCode;
import com.back.facepick.global.error.ErrorType;

public enum AuthErrorCode implements ErrorCode {
    AUTH_INVALID_TOKEN(ErrorType.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    AUTH_INVALID_REFRESH_TOKEN(ErrorType.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    AUTH_INVALID_CREDENTIALS(ErrorType.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_EMAIL_ALREADY_EXISTS(ErrorType.CONFLICT, "이미 가입된 이메일입니다."),
    AUTH_INVALID_PASSWORD(ErrorType.INVALID, "비밀번호는 8자 이상, 72바이트 이하여야 합니다."),
    AUTH_INVALID_EMAIL(ErrorType.INVALID, "이메일 형식이 올바르지 않습니다."),
    AUTH_VERIFICATION_RESEND_TOO_SOON(ErrorType.TOO_MANY_REQUESTS, "인증 코드는 60초에 한 번만 받을 수 있습니다."),
    AUTH_VERIFICATION_CODE_MISMATCH(ErrorType.INVALID, "인증 코드가 올바르지 않습니다."),
    AUTH_VERIFICATION_CODE_EXPIRED(ErrorType.INVALID, "인증 코드가 없거나 만료되었습니다. 코드를 다시 받아주세요."),
    AUTH_VERIFICATION_ATTEMPTS_EXCEEDED(ErrorType.INVALID, "인증 코드를 5번 틀렸습니다. 코드를 다시 받아주세요."),
    AUTH_VERIFICATION_MAIL_REJECTED(ErrorType.INVALID, "인증 메일을 보낼 수 없는 이메일 주소입니다."),
    AUTH_VERIFICATION_MAIL_UNAVAILABLE(ErrorType.EXTERNAL, "지금은 인증 메일을 보낼 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final ErrorType type;
    private final String message;

    AuthErrorCode(ErrorType type, String message) {
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
