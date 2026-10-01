package com.back.facepick.global.error;

// 도메인이 HTTP 를 모르도록 둔 에러 분류. HTTP 상태 변환은 ErrorHttpStatus 가 맡는다.
public enum ErrorType {
    INVALID,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    // 같은 요청을 너무 자주 보냄 (예: 인증 메일 재발송 간격)
    TOO_MANY_REQUESTS,
    INTERNAL,
    EXTERNAL;

    // 같은 요청을 다시 보내면 성공할 수 있는(요청이 아니라 서버·외부 서비스 쪽 원인인) 실패인지.
    public boolean isServerFault() {
        return this == INTERNAL || this == EXTERNAL;
    }
}
