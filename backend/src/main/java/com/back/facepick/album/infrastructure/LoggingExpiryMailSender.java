package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.ExpiryMailSender;
import com.back.facepick.album.domain.MailSendOutcome;
import lombok.extern.slf4j.Slf4j;

// Resend API 키가 없을 때(로컬 개발) 쓴다. 보내지 않았으니 보낸 것으로 치지 않고 영구 실패로 남긴다.
// 배포에서 키를 빠뜨려도 알림이 SENT 로 조용히 사라지지 않고, 키를 넣은 뒤 status 를 PENDING 으로 되돌려 다시 보낼 수 있다.
// 받는 주소는 로그에 남기지 않는다.
@Slf4j
public class LoggingExpiryMailSender implements ExpiryMailSender {
    private static final String DISABLED = "메일 발송 꺼짐 (RESEND_API_KEY 없음)";

    public LoggingExpiryMailSender() {
        log.warn("RESEND_API_KEY 가 없어 만료 알림 메일을 보내지 않는다. 알림은 FAILED 로 남는다");
    }

    @Override
    public MailSendOutcome send(ExpiryMail mail) {
        log.info("{} key={} albumId={}", DISABLED, mail.idempotencyKey(), mail.albumId());
        return MailSendOutcome.permanent(DISABLED);
    }
}
