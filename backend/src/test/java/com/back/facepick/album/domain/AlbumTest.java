package com.back.facepick.album.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.album.domain.exception.AlbumInvalidTitleException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlbumTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Nested
    @DisplayName("앨범 생성")
    class Create {

        @Test
        @DisplayName("앞뒤 공백을 지운 제목으로 만들고 30일 뒤에 만료된다")
        void createsWithExpiry() {
            // when
            Album album = Album.create(1L, "  제주 여행 ", NOW);

            // then
            assertThat(album.getTitle()).isEqualTo("제주 여행");
            assertThat(album.getOwnerId()).isEqualTo(1L);
            assertThat(album.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 12, 0));
        }

        @Test
        @DisplayName("빈 제목은 허용하지 않는다")
        void throwsWhenBlank() {
            // when & then
            assertThatThrownBy(() -> Album.create(1L, " ", NOW)).isInstanceOf(AlbumInvalidTitleException.class);
        }

        @Test
        @DisplayName("50자를 넘는 제목은 허용하지 않는다")
        void throwsWhenTooLong() {
            // when & then
            assertThatThrownBy(() -> Album.create(1L, "가".repeat(51), NOW))
                    .isInstanceOf(AlbumInvalidTitleException.class);
        }
    }
}
