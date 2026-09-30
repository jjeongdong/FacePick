package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.ExpiryMail;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

// 같은 ExpiryMail 이면 항상 같은 글이 나와야 한다. 내용이 바뀌면 같은 멱등 키 재시도를 Resend 가 409 로 거절한다.
// expires_at 은 서버 로컬 시각(KST)으로 저장돼 있어 그대로 표시한다.
public class ExpiryMailTemplate {
    private static final DateTimeFormatter SUBJECT_DATE = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN);
    private static final DateTimeFormatter BODY_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h시 mm분", Locale.KOREAN);

    private final String appBaseUrl;

    public ExpiryMailTemplate(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public String subject(ExpiryMail mail) {
        return "[facepick] '" + mail.albumTitle() + "' 앨범이 " + mail.expiresAt().format(SUBJECT_DATE) + "에 삭제돼요";
    }

    public String text(ExpiryMail mail) {
        return notice(mail.albumTitle(), mail) + "\n필요한 사진은 그 전에 받아 두세요.\n\n앨범 열기: " + albumUrl(mail);
    }

    public String html(ExpiryMail mail) {
        return "<p>" + notice(HtmlUtils.htmlEscape(mail.albumTitle()), mail) + "</p>"
                + "<p>필요한 사진은 그 전에 받아 두세요.</p>"
                + "<p><a href=\"" + albumUrl(mail) + "\">앨범 열기</a></p>";
    }

    private static String notice(String title, ExpiryMail mail) {
        return "'" + title + "' 앨범이 " + mail.expiresAt().format(BODY_DATE_TIME) + "에 삭제돼요.";
    }

    private String albumUrl(ExpiryMail mail) {
        return appBaseUrl + "/albums/" + mail.albumId();
    }
}
