package com.back.facepick.global.infrastructure.mail;

// 호출 결과를 해석(재시도할지, 사용자에게 뭐라 할지)하는 것은 쓰는 쪽이 정한다.
public record ResendResult(Type type, String messageId, Integer status, String body, String error) {

    public enum Type {
        SENT,
        // 서킷이 열려 있어 호출하지 않았다.
        NOT_ATTEMPTED,
        // 호출했지만 실패했다. status 가 null 이면 타임아웃·연결 실패다.
        FAILED
    }

    public static ResendResult sent(String messageId) {
        return new ResendResult(Type.SENT, messageId, null, null, null);
    }

    public static ResendResult notAttempted() {
        return new ResendResult(Type.NOT_ATTEMPTED, null, null, null, null);
    }

    public static ResendResult httpError(int status, String body) {
        return new ResendResult(Type.FAILED, null, status, body, status + " " + body);
    }

    public static ResendResult networkError(String error) {
        return new ResendResult(Type.FAILED, null, null, null, error);
    }

    public boolean isNetworkError() {
        return type == Type.FAILED && status == null;
    }
}
