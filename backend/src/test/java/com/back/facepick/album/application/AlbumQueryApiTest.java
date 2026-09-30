package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.back.facepick.album.application.dto.api.AlbumInfo;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.fixture.AlbumFixture;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlbumQueryApiTest {

    @Mock
    private AlbumMemberRepository albumMemberRepository;

    @Mock
    private AlbumRepository albumRepository;

    @InjectMocks
    private AlbumQueryApi albumQueryApi;

    @Test
    @DisplayName("참여자면 true")
    void returnsTrueForMember() {
        // given
        given(albumMemberRepository.existsByAlbumIdAndUserId(10L, 1L)).willReturn(true);

        // when & then
        assertThat(albumQueryApi.isMember(10L, 1L)).isTrue();
    }

    @Test
    @DisplayName("참여자가 아니면 false")
    void returnsFalseForNonMember() {
        // given
        given(albumMemberRepository.existsByAlbumIdAndUserId(10L, 2L)).willReturn(false);

        // when & then
        assertThat(albumQueryApi.isMember(10L, 2L)).isFalse();
    }

    @Test
    @DisplayName("앨범 ID·앨범장 ID·만료 시각을 조회한다")
    void getInfo() {
        // given
        given(albumRepository.getById(10L)).willReturn(AlbumFixture.album(10L, 1L));

        // when
        AlbumInfo info = albumQueryApi.getInfo(10L);

        // then
        assertThat(info).isEqualTo(new AlbumInfo(10L, 1L, AlbumFixture.NOW.plusDays(30)));
    }

    @Test
    @DisplayName("만료 앨범 ID 를 리포지토리에서 그대로 받아 준다")
    void findExpiredAlbumIds() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 12, 0);
        given(albumRepository.findExpiredIds(now, 10)).willReturn(List.of(3L, 1L));

        // when & then
        assertThat(albumQueryApi.findExpiredAlbumIds(now, 10)).containsExactly(3L, 1L);
    }
}
