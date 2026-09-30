package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.ExpiryMailSender;
import com.back.facepick.album.domain.MailSendOutcome;
import lombok.extern.slf4j.Slf4j;

// Resend API 키가 없는 로컬 개발용. 보내지 않고 보낸 것으로 친다. 받는 주소는 로그에 남기지 않는다.
@Slf4j
public class LoggingExpiryMailSender implements ExpiryMailSender {

    @Override
    public MailSendOutcome send(ExpiryMail mail) {
        log.info("RESEND_API_KEY 가 없어 만료 알림 메일을 보내지 않았다 key={} albumId={}", mail.idempotencyKey(), mail.albumId());
        return MailSendOutcome.sent("logged-" + mail.idempotencyKey());
    }
}
