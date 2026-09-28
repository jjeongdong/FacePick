package com.back.facepick.album.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.album.domain.exception.AlbumExpiredException;
import com.back.facepick.album.domain.exception.AlbumInvalidTitleException;
import com.back.facepick.album.domain.exception.AlbumNotOwnerException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlbumTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final String INVITE_CODE = "jeju-invite-code";

    @Nested
    @DisplayName("앨범 생성")
    class Create {

        @Test
        @DisplayName("앞뒤 공백을 지운 제목과 초대 코드로 만들고 30일 뒤에 만료된다")
        void createsWithExpiry() {
            // when
            Album album = Album.create(1L, "  제주 여행 ", NOW, INVITE_CODE);

            // then
            assertThat(album.getTitle()).isEqualTo("제주 여행");
            assertThat(album.getOwnerId()).isEqualTo(1L);
            assertThat(album.getInviteCode()).isEqualTo(INVITE_CODE);
            assertThat(album.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 12, 0));
        }

        @Test
        @DisplayName("빈 제목은 허용하지 않는다")
        void throwsWhenBlank() {
            // when & then
            assertThatThrownBy(() -> Album.create(1L, " ", NOW, INVITE_CODE))
                    .isInstanceOf(AlbumInvalidTitleException.class);
        }

        @Test
        @DisplayName("50자를 넘는 제목은 허용하지 않는다")
        void throwsWhenTooLong() {
            // when & then
            assertThatThrownBy(() -> Album.create(1L, "가".repeat(51), NOW, INVITE_CODE))
                    .isInstanceOf(AlbumInvalidTitleException.class);
        }
    }

    @Nested
    @DisplayName("초대 코드 재발급")
    class ReissueInviteCode {

        @Test
        @DisplayName("앨범장은 초대 코드를 새 코드로 바꾼다")
        void replacesCodeWhenOwner() {
            // given
            Album album = Album.create(1L, "제주 여행", NOW, INVITE_CODE);

            // when
            album.reissueInviteCode(1L, "new-invite-code");

            // then
            assertThat(album.getInviteCode()).isEqualTo("new-invite-code");
        }

        @Test
        @DisplayName("앨범장이 아니면 재발급할 수 없고 코드는 그대로다")
        void throwsWhenNotOwner() {
            // given
            Album album = Album.create(1L, "제주 여행", NOW, INVITE_CODE);

            // when & then
            assertThatThrownBy(() -> album.reissueInviteCode(2L, "new-invite-code"))
                    .isInstanceOf(AlbumNotOwnerException.class);
            assertThat(album.getInviteCode()).isEqualTo(INVITE_CODE);
        }
    }

    @Nested
    @DisplayName("앨범 참여")
    class Join {

        @Test
        @DisplayName("만료 전이면 MEMBER 참여자를 만든다")
        void createsMemberBeforeExpiry() {
            // given
            Album album = Album.create(1L, "제주 여행", NOW, INVITE_CODE);

            // when
            AlbumMember member = album.join(2L, NOW.plusDays(29));

            // then
            assertThat(member.getAlbum()).isSameAs(album);
            assertThat(member.getUserId()).isEqualTo(2L);
            assertThat(member.getRole()).isEqualTo(AlbumRole.MEMBER);
        }

        @Test
        @DisplayName("만료 시각과 같으면 만료로 보고 참여할 수 없다")
        void throwsAtExpiry() {
            // given
            Album album = Album.create(1L, "제주 여행", NOW, INVITE_CODE);

            // when & then
            assertThatThrownBy(() -> album.join(2L, album.getExpiresAt())).isInstanceOf(AlbumExpiredException.class);
        }
    }
}
