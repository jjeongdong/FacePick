package com.back.facepick.album.domain;

public record MailSendOutcome(Type type, String providerMessageId, String error) {

    public enum Type {
        SENT,
        // 잠시 뒤 다시 보내면 될 수 있다 (타임아웃, 429, 5xx 등)
        RETRYABLE,
        // 다시 보내도 같은 결과다 (잘못된 주소, 인증 실패 등)
        PERMANENT
    }

    public static MailSendOutcome sent(String providerMessageId) {
        return new MailSendOutcome(Type.SENT, providerMessageId, null);
    }

    public static MailSendOutcome retryable(String error) {
        return new MailSendOutcome(Type.RETRYABLE, null, error);
    }

    public static MailSendOutcome permanent(String error) {
        return new MailSendOutcome(Type.PERMANENT, null, error);
    }
}
