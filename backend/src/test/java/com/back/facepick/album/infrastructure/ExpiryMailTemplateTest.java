package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.album.domain.ExpiryMail;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpiryMailTemplateTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 7, 15, 0);
    private final ExpiryMailTemplate template = new ExpiryMailTemplate("http://localhost:5173");

    private static ExpiryMail mail(String title) {
        return new ExpiryMail("album-expiry-notice-1", "me@example.com", 13L, title, EXPIRES_AT);
    }

    @Test
    @DisplayName("제목에 앨범 제목과 삭제 날짜를 넣는다")
    void subject() {
        assertThat(template.subject(mail("제주 여행"))).isEqualTo("[facepick] '제주 여행' 앨범이 10월 7일에 삭제돼요");
    }

    @Test
    @DisplayName("텍스트 본문에 삭제 시각과 앨범 링크를 넣는다")
    void text() {
        // when
        String text = template.text(mail("제주 여행"));

        // then
        assertThat(text)
                .contains("'제주 여행' 앨범이 2026년 10월 7일 오후 3시 00분에 삭제돼요.")
                .contains("필요한 사진은 그 전에 받아 두세요.")
                .contains("http://localhost:5173/albums/13");
    }

    @Test
    @DisplayName("HTML 본문은 앨범 제목을 이스케이프한다")
    void escapesTitleInHtml() {
        // when
        String html = template.html(mail("<b>Tom & 'Jerry'</b>"));

        // then
        assertThat(html)
                .contains("&lt;b&gt;Tom &amp; &#39;Jerry&#39;&lt;/b&gt;")
                .doesNotContain("<b>Tom")
                .contains("<a href=\"http://localhost:5173/albums/13\">");
    }
}
