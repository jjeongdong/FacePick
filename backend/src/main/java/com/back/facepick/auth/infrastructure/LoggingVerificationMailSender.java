package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.VerificationMailSender;
import lombok.extern.slf4j.Slf4j;

// Resend API 키가 없을 때(로컬 개발) 쓴다. 메일 없이는 가입할 수 없으므로 코드를 로그로 보여 준다.
// 키가 없는 로컬에서만 뜨는 구현이라 코드를 남기되, 받는 주소는 남기지 않는다.
@Slf4j
public class LoggingVerificationMailSender implements VerificationMailSender {

    public LoggingVerificationMailSender() {
        log.warn("RESEND_API_KEY 가 없어 인증 메일을 보내지 않고 코드를 로그에 남긴다 (로컬 개발 전용)");
    }

    @Override
    public void send(String email, String code) {
        log.info("인증 메일 발송 꺼짐 — 인증 코드 code={}", code);
    }
}
