package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import com.back.facepick.album.application.dto.command.AlbumJoinCommand;
import com.back.facepick.album.application.dto.result.AlbumCreateResult;
import com.back.facepick.album.application.dto.result.AlbumInviteResult;
import com.back.facepick.album.application.dto.result.AlbumJoinResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.album.domain.InviteCodeGenerator;
import com.back.facepick.album.domain.exception.AlbumExpiredException;
import com.back.facepick.album.domain.exception.AlbumNotOwnerException;
import com.back.facepick.album.fixture.AlbumFixture;
import java.time.LocalDateTime;
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

    @Nested
    @DisplayName("초대 코드 재발급")
    class ReissueAlbumInvite {

        @Test
        @DisplayName("앨범장은 새 초대 코드를 받는다")
        void returnsNewCodeWhenOwner() {
            // given
            given(albumRepository.getById(10L)).willReturn(AlbumFixture.album(10L, 1L));
            given(inviteCodeGenerator.generate()).willReturn("new-invite-code");

            // when
            AlbumInviteResult result = albumCommandService.reissueAlbumInvite(1L, 10L);

            // then
            assertThat(result).isEqualTo(new AlbumInviteResult("new-invite-code"));
        }

        @Test
        @DisplayName("앨범장이 아니면 AlbumNotOwnerException")
        void throwsWhenNotOwner() {
            // given
            given(albumRepository.getById(10L)).willReturn(AlbumFixture.album(10L, 1L));
            given(inviteCodeGenerator.generate()).willReturn("new-invite-code");

            // when & then
            assertThatThrownBy(() -> albumCommandService.reissueAlbumInvite(2L, 10L))
                    .isInstanceOf(AlbumNotOwnerException.class);
        }
    }

    @Nested
    @DisplayName("초대 코드로 참여")
    class JoinAlbum {

        private static final AlbumJoinCommand COMMAND = new AlbumJoinCommand(AlbumFixture.INVITE_CODE);

        @Test
        @DisplayName("처음 참여하면 MEMBER 로 등록하고 앨범 정보를 돌려준다")
        void registersNewMember() {
            // given
            Album album = AlbumFixture.album(10L, 1L, LocalDateTime.now());
            given(albumRepository.getByInviteCode(AlbumFixture.INVITE_CODE)).willReturn(album);
            given(albumMemberRepository.existsByAlbumIdAndUserId(10L, 2L)).willReturn(false);

            // when
            AlbumJoinResult result = albumCommandService.joinAlbum(2L, COMMAND);

            // then
            assertThat(result).isEqualTo(new AlbumJoinResult(10L, "제주 여행"));
            then(albumMemberRepository)
                    .should()
                    .save(argThat(member -> member.getAlbum() == album
                            && member.getUserId().equals(2L)
                            && member.getRole() == AlbumRole.MEMBER));
        }

        @Test
        @DisplayName("앨범장이 자기 코드로 참여하면 참여자를 새로 만들지 않고 그대로 성공한다")
        void succeedsWithoutSavingWhenAlreadyMember() {
            // given
            given(albumRepository.getByInviteCode(AlbumFixture.INVITE_CODE))
                    .willReturn(AlbumFixture.album(10L, 1L, LocalDateTime.now()));
            given(albumMemberRepository.existsByAlbumIdAndUserId(10L, 1L)).willReturn(true);

            // when
            AlbumJoinResult result = albumCommandService.joinAlbum(1L, COMMAND);

            // then
            assertThat(result).isEqualTo(new AlbumJoinResult(10L, "제주 여행"));
            then(albumMemberRepository).should(never()).save(any(AlbumMember.class));
        }

        @Test
        @DisplayName("만료된 앨범이라도 이미 참여자면 그대로 성공한다")
        void succeedsWhenAlreadyMemberOfExpiredAlbum() {
            // given
            given(albumRepository.getByInviteCode(AlbumFixture.INVITE_CODE))
                    .willReturn(AlbumFixture.album(10L, 1L, LocalDateTime.now().minusDays(31)));
            given(albumMemberRepository.existsByAlbumIdAndUserId(10L, 2L)).willReturn(true);

            // when
            AlbumJoinResult result = albumCommandService.joinAlbum(2L, COMMAND);

            // then
            assertThat(result).isEqualTo(new AlbumJoinResult(10L, "제주 여행"));
        }

        @Test
        @DisplayName("만료된 앨범에 새로 참여하면 AlbumExpiredException 이고 저장하지 않는다")
        void throwsWhenExpired() {
            // given
            given(albumRepository.getByInviteCode(AlbumFixture.INVITE_CODE))
                    .willReturn(AlbumFixture.album(10L, 1L, LocalDateTime.now().minusDays(31)));
            given(albumMemberRepository.existsByAlbumIdAndUserId(10L, 2L)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> albumCommandService.joinAlbum(2L, COMMAND))
                    .isInstanceOf(AlbumExpiredException.class);
            then(albumMemberRepository).should(never()).save(any(AlbumMember.class));
        }
    }
}
