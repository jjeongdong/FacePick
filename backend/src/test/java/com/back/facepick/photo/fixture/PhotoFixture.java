package com.back.facepick.photo.fixture;

import com.back.facepick.photo.domain.Photo;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class PhotoFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);
    public static final String HASH = "a".repeat(64);
    public static final Long BYTE_SIZE = 1000L;

    private PhotoFixture() {}

    public static Photo pending(Long photoId, Long albumId, Long uploaderId) {
        return pending(photoId, albumId, uploaderId, HASH);
    }

    public static Photo pending(Long photoId, Long albumId, Long uploaderId, String hash) {
        Photo photo = Photo.create(albumId, uploaderId, hash, BYTE_SIZE, "image/jpeg");
        // 저장 없이 쓰는 단위 테스트용이라 id 를 리플렉션으로 채운다.
        ReflectionTestUtils.setField(photo, "id", photoId);
        return photo;
    }

    public static Photo uploaded(Long photoId, Long albumId, Long uploaderId, String hash) {
        Photo photo = pending(photoId, albumId, uploaderId, hash);
        photo.complete(uploaderId, BYTE_SIZE, NOW);
        return photo;
    }

    public static Photo processed(Long photoId, Long albumId, Long uploaderId, String hash) {
        Photo photo = uploaded(photoId, albumId, uploaderId, hash);
        // 썸네일 워커만 쓰는 읽기 전용 컬럼이라 리플렉션으로 채운다.
        ReflectionTestUtils.setField(photo, "thumbnailKey", "albums/" + albumId + "/thumbnails/" + hash + ".jpg");
        ReflectionTestUtils.setField(photo, "previewKey", "albums/" + albumId + "/previews/" + hash + ".jpg");
        ReflectionTestUtils.setField(photo, "width", 4032);
        ReflectionTestUtils.setField(photo, "height", 3024);
        ReflectionTestUtils.setField(photo, "takenAt", NOW.minusDays(3));
        ReflectionTestUtils.setField(photo, "processedAt", NOW.plusMinutes(1));
        return photo;
    }
}
