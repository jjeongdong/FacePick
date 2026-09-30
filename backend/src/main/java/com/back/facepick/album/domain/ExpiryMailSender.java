package com.back.facepick.album.domain;

public interface ExpiryMailSender {
    /** 예외를 던지지 않고, 실패도 재시도 가능 여부를 담은 결과로 돌려준다. */
    MailSendOutcome send(ExpiryMail mail);
}
