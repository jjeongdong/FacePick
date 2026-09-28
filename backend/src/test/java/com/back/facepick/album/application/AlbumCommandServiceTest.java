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
import com.back.facepick.album.domain.InviteCodeGenerator;
import com.back.facepick.album.fixture.AlbumFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Mock
    private InviteCodeGenerator inviteCodeGenerator;

    @InjectMocks
    private AlbumCommandService albumCommandService;

    @Nested
    @DisplayName("앨범 생성")
    class CreateAlbum {

        @Test
        @DisplayName("초대 코드를 발급해 앨범을 만들고 만든 사람을 앨범장으로 등록한다")
        void createsAlbumWithInviteCodeAndOwner() {
            // given
            Album album = AlbumFixture.album(10L, 1L);
            given(inviteCodeGenerator.generate()).willReturn(AlbumFixture.INVITE_CODE);
            given(albumRepository.save(any(Album.class))).willReturn(album);

            // when
            AlbumCreateResult result = albumCommandService.createAlbum(1L, new AlbumCreateCommand("제주 여행"));

            // then
            assertThat(result)
                    .isEqualTo(new AlbumCreateResult(
                            10L, "제주 여행", AlbumFixture.INVITE_CODE, album.getExpiresAt(), AlbumFixture.NOW));
            then(albumRepository).should().save(argThat(saved -> saved.getInviteCode()
                    .equals(AlbumFixture.INVITE_CODE)));
            then(albumMemberRepository)
                    .should()
                    .save(argThat(member -> member.getAlbum() == album
                            && member.getUserId().equals(1L)
                            && member.getRole() == AlbumRole.OWNER));
        }
    }
}
