package com.back.facepick.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.global.error.InvalidInputException;
import com.back.facepick.photo.domain.PhotoCursor;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhotoCursorCodecTest {

    @Test
    @DisplayName("마이크로초까지 담아 인코딩한 커서를 그대로 되돌린다")
    void roundTrips() {
        // given
        PhotoCursor cursor = new PhotoCursor(LocalDateTime.of(2026, 9, 1, 12, 0, 0, 123_456_000), 42L);

        // when
        PhotoCursor decoded = PhotoCursorCodec.decode(PhotoCursorCodec.encode(cursor));

        // then
        assertThat(decoded).isEqualTo(cursor);
    }

    @Test
    @DisplayName("URL 에 그대로 넣을 수 있는 문자만 쓴다")
    void isUrlSafe() {
        // when
        String encoded = PhotoCursorCodec.encode(new PhotoCursor(LocalDateTime.of(2026, 9, 1, 12, 0), 42L));

        // then
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    // !!! = base64 아님, abc = 구분자 없음, 나머지는 "2026-09-01T12:00", "not-a-date_12", "2026-09-01T12:00_x"
    @ParameterizedTest
    @ValueSource(strings = {"!!!", "abc", "MjAyNi0wOS0wMVQxMjowMA", "bm90LWEtZGF0ZV8xMg", "MjAyNi0wOS0wMVQxMjowMF94"})
    @DisplayName("base64 가 아니거나 형식·날짜·숫자가 틀리면 InvalidInputException")
    void rejectsMalformed(String cursor) {
        // when & then
        assertThatThrownBy(() -> PhotoCursorCodec.decode(cursor)).isInstanceOf(InvalidInputException.class);
    }

    // "+999999999-12-31T23:59:59_1", "-000001-01-01T00:00_1" — Java 는 읽지만 DB 타임스탬프 범위를 넘는다.
    @ParameterizedTest
    @ValueSource(strings = {"Kzk5OTk5OTk5OS0xMi0zMVQyMzo1OTo1OV8x", "LTAwMDAwMS0wMS0wMVQwMDowMF8x"})
    @DisplayName("연도가 1~9999 밖이면 InvalidInputException")
    void rejectsOutOfRangeYear(String cursor) {
        // when & then
        assertThatThrownBy(() -> PhotoCursorCodec.decode(cursor)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("구분자 뒤가 비어 있으면 InvalidInputException")
    void rejectsMissingPhotoId() {
        // given
        String cursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("2026-09-01T12:00:00_".getBytes(StandardCharsets.UTF_8));

        // when & then
        assertThatThrownBy(() -> PhotoCursorCodec.decode(cursor)).isInstanceOf(InvalidInputException.class);
    }
}
