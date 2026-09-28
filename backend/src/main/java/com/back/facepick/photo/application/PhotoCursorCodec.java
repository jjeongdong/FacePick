package com.back.facepick.photo.application;

import com.back.facepick.global.error.InvalidInputException;
import com.back.facepick.photo.domain.PhotoCursor;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;

// nextCursor 는 정렬 키를 인코딩한 문자열 하나다 (presentation 페이징 규칙). 두 키를 "시각_ID" 로 묶는다.
final class PhotoCursorCodec {
    private static final String SEPARATOR = "_";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MIN_YEAR = 1;
    private static final int MAX_YEAR = 9999;

    private PhotoCursorCodec() {}

    static String encode(PhotoCursor cursor) {
        String raw = cursor.uploadedAt().format(FORMATTER) + SEPARATOR + cursor.photoId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // 클라이언트가 보낸 값이라 어떤 형식 오류든 500 이 아닌 400 으로 돌려준다.
    static PhotoCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separatorIndex = raw.lastIndexOf(SEPARATOR);
            if (separatorIndex < 0) {
                throw new InvalidInputException();
            }
            LocalDateTime uploadedAt = LocalDateTime.parse(raw.substring(0, separatorIndex), FORMATTER);
            // ISO 형식은 ±999999999 년까지 읽는다. DB 타임스탬프 범위를 넘는 값이 쿼리까지 가서 500 이 되지 않게 막는다.
            if (uploadedAt.getYear() < MIN_YEAR || uploadedAt.getYear() > MAX_YEAR) {
                throw new InvalidInputException();
            }
            Long photoId = Long.parseLong(raw.substring(separatorIndex + 1));
            return new PhotoCursor(uploadedAt, photoId);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidInputException();
        }
    }
}
