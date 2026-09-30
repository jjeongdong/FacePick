package com.back.facepick.album.domain;

public interface ExpiryMailSender {
    /** 예외를 던지지 않고, 실패도 재시도 가능 여부를 담은 결과로 돌려준다. */
    MailSendOutcome send(ExpiryMail mail);

    /** 지금 호출을 받아 주는지. false 면 발송기는 이번 회차에 행을 선점하지 않는다. */
    default boolean isAvailable() {
        return true;
    }
}
