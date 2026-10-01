package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.ExpiryMailSender;
import com.back.facepick.album.domain.MailSendOutcome;
import com.back.facepick.global.infrastructure.mail.ResendClient;
import com.back.facepick.global.infrastructure.mail.ResendEmail;
import com.back.facepick.global.infrastructure.mail.ResendResult;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

// Idempotency-Key: 24시간 동안 같은 키 + 같은 내용이면 다시 보내지 않고 처음 메일 ID 를 돌려준다.
// 호출·서킷은 ResendClient 가 맡고, 여기서는 결과를 발송기의 재시도 규칙(MailSendOutcome)으로 옮긴다.
@Slf4j
public class ResendExpiryMailSender implements ExpiryMailSender {
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int CONFLICT = 409;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int SERVER_ERROR_MIN = 500;
    // 같은 키 요청이 아직 처리 중 — 잠시 뒤 다시 보내면 된다.
    private static final String CONCURRENT_IDEMPOTENT = "concurrent_idempotent_requests";
    private static final Pattern ERROR_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final ResendClient resendClient;
    private final ExpiryMailTemplate template;
    private final Duration notAttemptedDelay;

    public ResendExpiryMailSender(ResendClient resendClient, ExpiryMailTemplate template, Duration notAttemptedDelay) {
        this.resendClient = resendClient;
        this.template = template;
        this.notAttemptedDelay = notAttemptedDelay;
    }

    @Override
    public boolean isAvailable() {
        return resendClient.isAvailable();
    }

    @Override
    public MailSendOutcome send(ExpiryMail mail) {
        ResendResult result = resendClient.send(new ResendEmail(
                mail.to(), template.subject(mail), template.text(mail), template.html(mail), mail.idempotencyKey()));
        return switch (result.type()) {
            case SENT -> MailSendOutcome.sent(result.messageId());
            case NOT_ATTEMPTED -> MailSendOutcome.notAttempted(
                    LocalDateTime.now().plus(notAttemptedDelay));
            case FAILED -> classify(result, mail);
        };
    }

    private MailSendOutcome classify(ResendResult result, ExpiryMail mail) {
        if (result.isNetworkError()) {
            // 타임아웃·연결 실패는 Resend 가 받았는지 모르지만 같은 키로 다시 보내면 중복되지 않는다.
            return MailSendOutcome.retryable(result.error());
        }
        int status = result.status();
        String error = result.error();
        if (status == TOO_MANY_REQUESTS
                || status >= SERVER_ERROR_MIN
                || (status == CONFLICT && error.contains(CONCURRENT_IDEMPOTENT))) {
            return MailSendOutcome.retryable(error);
        }
        if (status == UNAUTHORIZED || status == FORBIDDEN || status == CONFLICT) {
            // 키·발신 주소 설정 문제이거나, 재시도 사이 메일 내용이 바뀐 버그다. 다시 보내도 같다.
            // 응답 본문(message)에는 계정 이메일이 들어 있을 수 있어 로그에는 상태와 오류 이름만 남긴다.
            log.error(
                    "Resend 설정 또는 멱등 키 오류로 만료 알림을 보낼 수 없다 key={} status={} name={}",
                    mail.idempotencyKey(),
                    status,
                    errorName(result.body()));
        }
        return MailSendOutcome.permanent(error);
    }

    private static String errorName(String body) {
        Matcher matcher = ERROR_NAME.matcher(body);
        return matcher.find() ? matcher.group(1) : "unknown";
    }
}
