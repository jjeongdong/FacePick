package com.back.facepick.photo.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.photo.domain.exception.PhotoDeleteAlbumExpiredException;
import com.back.facepick.photo.domain.exception.PhotoDeleteNotAlbumMemberException;
import com.back.facepick.photo.domain.exception.PhotoNotDeletableException;
import com.back.facepick.photo.fixture.PhotoFixture;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhotoDeletePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final Long OWNER_ID = 99L;
    private static final Photo MINE = PhotoFixture.uploaded(1L, 10L, 1L, "a".repeat(64));
    private static final Photo OTHERS = PhotoFixture.uploaded(2L, 10L, 2L, "b".repeat(64));

    @Test
    @DisplayName("참여자가 자기 사진만 지우면 통과한다")
    void passesForOwnPhotos() {
        // when & then
        assertThatCode(() -> PhotoDeletePolicy.validate(true, NOW.plusDays(1), NOW, 1L, OWNER_ID, List.of(MINE)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("앨범장은 남의 사진이 섞여도 통과한다")
    void passesForOwner() {
        // when & then
        assertThatCode(() -> PhotoDeletePolicy.validate(
                        true, NOW.plusDays(1), NOW, OWNER_ID, OWNER_ID, List.of(MINE, OTHERS)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("지울 사진이 없어도(모두 건너뜀) 참여자면 통과한다")
    void passesForEmpty() {
        // when & then
        assertThatCode(() -> PhotoDeletePolicy.validate(true, NOW.plusDays(1), NOW, 1L, OWNER_ID, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("참여자가 아니면 PhotoDeleteNotAlbumMemberException")
    void throwsWhenNotMember() {
        // when & then
        assertThatThrownBy(() -> PhotoDeletePolicy.validate(false, NOW.plusDays(1), NOW, 1L, OWNER_ID, List.of(MINE)))
                .isInstanceOf(PhotoDeleteNotAlbumMemberException.class);
    }

    @Test
    @DisplayName("만료 시각과 같으면 만료로 보고 PhotoDeleteAlbumExpiredException")
    void throwsAtExpiry() {
        // when & then
        assertThatThrownBy(() -> PhotoDeletePolicy.validate(true, NOW, NOW, 1L, OWNER_ID, List.of(MINE)))
                .isInstanceOf(PhotoDeleteAlbumExpiredException.class);
    }

    @Test
    @DisplayName("남의 사진이 한 장이라도 섞이면 PhotoNotDeletableException")
    void throwsWhenAnyPhotoIsNotDeletable() {
        // when & then
        assertThatThrownBy(() ->
                        PhotoDeletePolicy.validate(true, NOW.plusDays(1), NOW, 1L, OWNER_ID, List.of(MINE, OTHERS)))
                .isInstanceOf(PhotoNotDeletableException.class);
    }
}
