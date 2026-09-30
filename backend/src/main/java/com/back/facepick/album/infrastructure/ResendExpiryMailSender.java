package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.ExpiryMail;
import com.back.facepick.album.domain.ExpiryMailSender;
import com.back.facepick.album.domain.MailSendOutcome;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// https://resend.com/docs/api-reference/emails/send-email
// Idempotency-Key: 24시간 동안 같은 키 + 같은 내용이면 다시 보내지 않고 처음 메일 ID 를 돌려준다.
@Slf4j
public class ResendExpiryMailSender implements ExpiryMailSender {
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int CONFLICT = 409;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    // 같은 키 요청이 아직 처리 중 — 잠시 뒤 다시 보내면 된다.
    private static final String CONCURRENT_IDEMPOTENT = "concurrent_idempotent_requests";
    private static final Pattern ERROR_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final RestClient restClient;
    private final String from;
    private final ExpiryMailTemplate template;

    public ResendExpiryMailSender(RestClient restClient, String from, ExpiryMailTemplate template) {
        this.restClient = restClient;
        this.from = from;
        this.template = template;
    }

    @Override
    public MailSendOutcome send(ExpiryMail mail) {
        try {
            SendEmailReply reply = restClient
                    .post()
                    .uri("/emails")
                    .header("Idempotency-Key", mail.idempotencyKey())
                    .body(new SendEmailBody(
                            from, List.of(mail.to()), template.subject(mail), template.text(mail), template.html(mail)))
                    .retrieve()
                    .body(SendEmailReply.class);
            return MailSendOutcome.sent(reply == null ? null : reply.id());
        } catch (HttpStatusCodeException e) {
            return classify(e, mail);
        } catch (RestClientException e) {
            // 타임아웃·연결 실패: Resend 가 받았는지 모르지만 같은 키로 다시 보내면 중복되지 않는다.
            return MailSendOutcome.retryable(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private MailSendOutcome classify(HttpStatusCodeException e, ExpiryMail mail) {
        int status = e.getStatusCode().value();
        String error = status + " " + e.getResponseBodyAsString();
        if (status == TOO_MANY_REQUESTS
                || e.getStatusCode().is5xxServerError()
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
                    errorName(e.getResponseBodyAsString()));
        }
        return MailSendOutcome.permanent(error);
    }

    private static String errorName(String body) {
        Matcher matcher = ERROR_NAME.matcher(body);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    record SendEmailBody(String from, List<String> to, String subject, String text, String html) {}

    record SendEmailReply(String id) {}
}
