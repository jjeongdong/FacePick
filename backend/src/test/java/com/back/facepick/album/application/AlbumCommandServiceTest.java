package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.album.fixture.AlbumFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlbumCommandServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private AlbumMemberRepository albumMemberRepository;

    @InjectMocks
    private AlbumCommandService albumCommandService;

    @Test
    @DisplayName("앨범을 만들면 만든 사람을 앨범장으로 등록한다")
    void createAlbumRegistersOwner() {
        // given
        Album album = AlbumFixture.album(10L, 1L);
        given(albumRepository.save(any(Album.class))).willReturn(album);

        // when
        AlbumCreateResult result = albumCommandService.createAlbum(1L, new AlbumCreateCommand("제주 여행"));

        // then
        assertThat(result).isEqualTo(new AlbumCreateResult(10L, "제주 여행", album.getExpiresAt(), AlbumFixture.NOW));
        then(albumMemberRepository)
                .should()
                .save(argThat(member -> member.getAlbum() == album
                        && member.getUserId().equals(1L)
                        && member.getRole() == AlbumRole.OWNER));
    }
}
