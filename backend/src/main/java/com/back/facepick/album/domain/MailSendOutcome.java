package com.back.facepick.album.domain;

import java.time.LocalDateTime;

public record MailSendOutcome(Type type, String providerMessageId, String error, LocalDateTime retryAt) {

    public enum Type {
        SENT,
        // 잠시 뒤 다시 보내면 될 수 있다 (타임아웃, 429, 5xx 등)
        RETRYABLE,
        // 다시 보내도 같은 결과다 (잘못된 주소, 인증 실패 등)
        PERMANENT,
        // 메일 서비스가 장애 중이라(서킷 OPEN) 호출하지 않았다. 시도 횟수에 넣지 않고 retryAt 에 다시 본다.
        NOT_ATTEMPTED
    }

    public static MailSendOutcome sent(String providerMessageId) {
        return new MailSendOutcome(Type.SENT, providerMessageId, null, null);
    }

    public static MailSendOutcome retryable(String error) {
        return new MailSendOutcome(Type.RETRYABLE, null, error, null);
    }

    public static MailSendOutcome permanent(String error) {
        return new MailSendOutcome(Type.PERMANENT, null, error, null);
    }

    public static MailSendOutcome notAttempted(LocalDateTime retryAt) {
        return new MailSendOutcome(Type.NOT_ATTEMPTED, null, null, retryAt);
    }
}
