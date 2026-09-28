package com.back.facepick.photo.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.photo.domain.exception.PhotoAlbumExpiredException;
import com.back.facepick.photo.domain.exception.PhotoNotAlbumMemberException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhotoUploadPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Test
    @DisplayName("참여자이고 만료 전이면 통과한다")
    void passesForMemberBeforeExpiry() {
        // when & then
        assertThatCode(() -> PhotoUploadPolicy.validate(true, NOW.plusDays(1), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("참여자가 아니면 PhotoNotAlbumMemberException")
    void throwsWhenNotMember() {
        // when & then
        assertThatThrownBy(() -> PhotoUploadPolicy.validate(false, NOW.plusDays(1), NOW))
                .isInstanceOf(PhotoNotAlbumMemberException.class);
    }

    @Test
    @DisplayName("만료 시각과 같으면 만료로 보고 PhotoAlbumExpiredException")
    void throwsAtExpiry() {
        // when & then
        assertThatThrownBy(() -> PhotoUploadPolicy.validate(true, NOW, NOW))
                .isInstanceOf(PhotoAlbumExpiredException.class);
    }
}
