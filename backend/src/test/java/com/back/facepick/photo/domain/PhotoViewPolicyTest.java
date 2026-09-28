package com.back.facepick.photo.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.photo.domain.exception.PhotoViewAlbumExpiredException;
import com.back.facepick.photo.domain.exception.PhotoViewNotAlbumMemberException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhotoViewPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Test
    @DisplayName("참여자이고 만료 전이면 통과한다")
    void passesForMemberBeforeExpiry() {
        // when & then
        assertThatCode(() -> PhotoViewPolicy.validate(true, NOW.plusDays(1), NOW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("참여자가 아니면 PhotoViewNotAlbumMemberException")
    void throwsWhenNotMember() {
        // when & then
        assertThatThrownBy(() -> PhotoViewPolicy.validate(false, NOW.plusDays(1), NOW))
                .isInstanceOf(PhotoViewNotAlbumMemberException.class);
    }

    @Test
    @DisplayName("만료 시각과 같으면 만료로 보고 PhotoViewAlbumExpiredException")
    void throwsAtExpiry() {
        // when & then
        assertThatThrownBy(() -> PhotoViewPolicy.validate(true, NOW, NOW))
                .isInstanceOf(PhotoViewAlbumExpiredException.class);
    }
}
