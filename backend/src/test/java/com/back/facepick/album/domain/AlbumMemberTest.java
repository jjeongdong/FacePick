package com.back.facepick.album.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlbumMemberTest {

    @Test
    @DisplayName("앨범·사용자·역할로 참여자를 만든다")
    void createsMember() {
        // given
        Album album = Album.create(1L, "제주 여행", LocalDateTime.of(2026, 9, 1, 12, 0));

        // when
        AlbumMember member = AlbumMember.create(album, 2L, AlbumRole.MEMBER);

        // then
        assertThat(member.getAlbum()).isSameAs(album);
        assertThat(member.getUserId()).isEqualTo(2L);
        assertThat(member.getRole()).isEqualTo(AlbumRole.MEMBER);
    }
}
