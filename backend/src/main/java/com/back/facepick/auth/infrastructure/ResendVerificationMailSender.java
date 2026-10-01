package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.VerificationMailSender;
import com.back.facepick.auth.domain.exception.AuthVerificationMailRejectedException;
import com.back.facepick.auth.domain.exception.AuthVerificationMailUnavailableException;
import com.back.facepick.global.infrastructure.mail.ResendClient;
import com.back.facepick.global.infrastructure.mail.ResendEmail;
import com.back.facepick.global.infrastructure.mail.ResendResult;
import io.github.resilience4j.bulkhead.Bulkhead;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

// 요청 스레드(Tomcat)에서 Resend 를 기다리므로 Bulkhead 로 동시에 기다리는 수를 제한한다.
// Resend 가 느려져도 스레드는 최대 max-concurrent-calls 개만 붙잡히고 나머지 API 는 계속 돈다.
// 순서는 Bulkhead(바깥) → 서킷(ResendClient 안). 자리가 없어 거절한 요청은 서킷 통계에 넣지 않는다.
// 재시도는 사용자가 다시 누르는 것으로 대신해 멱등 키를 쓰지 않는다 (코드는 요청마다 새로 만든다).
@Slf4j
public class ResendVerificationMailSender implements VerificationMailSender {
    private static final int BAD_REQUEST = 400;
    private static final int UNPROCESSABLE = 422;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final Pattern ERROR_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final ResendClient resendClient;
    private final Bulkhead bulkhead;
    private final VerificationMailTemplate template;

    public ResendVerificationMailSender(
            ResendClient resendClient, Bulkhead bulkhead, VerificationMailTemplate template) {
        this.resendClient = resendClient;
        this.bulkhead = bulkhead;
        this.template = template;
    }

    @Override
    public void send(String email, String code) {
        if (!bulkhead.tryAcquirePermission()) {
            throw new AuthVerificationMailUnavailableException();
        }
        ResendResult result;
        try {
            result = resendClient.send(
                    new ResendEmail(email, template.subject(code), template.text(code), template.html(code), null));
        } finally {
            bulkhead.onComplete();
        }
        switch (result.type()) {
            case SENT -> {
                return;
            }
            case NOT_ATTEMPTED -> throw new AuthVerificationMailUnavailableException();
            case FAILED -> throw failure(result);
        }
    }

    private RuntimeException failure(ResendResult result) {
        if (result.isNetworkError()) {
            return new AuthVerificationMailUnavailableException();
        }
        int status = result.status();
        if (status == BAD_REQUEST || status == UNPROCESSABLE) {
            return new AuthVerificationMailRejectedException();
        }
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            // 키·발신 도메인 설정 문제라 사람이 고쳐야 한다. 응답 본문에는 계정 이메일이 있을 수 있어 이름만 남긴다.
            log.error("Resend 설정 오류로 인증 메일을 보낼 수 없다 status={} name={}", status, errorName(result.body()));
        }
        return new AuthVerificationMailUnavailableException();
    }

    private static String errorName(String body) {
        Matcher matcher = ERROR_NAME.matcher(body);
        return matcher.find() ? matcher.group(1) : "unknown";
    }
}
