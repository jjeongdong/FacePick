package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.back.facepick.album.application.dto.result.AlbumDetailResult;
import com.back.facepick.album.application.dto.result.AlbumResult;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMemberRepository;
import com.back.facepick.album.domain.AlbumRepository;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.album.domain.exception.AlbumNotMemberException;
import com.back.facepick.album.fixture.AlbumFixture;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.application.dto.api.UserInfo;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlbumQueryServiceTest {

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private AlbumMemberRepository albumMemberRepository;

    @Mock
    private UserQueryApi userQueryApi;

    @InjectMocks
    private AlbumQueryService albumQueryService;

    @Nested
    @DisplayName("앨범 조회")
    class GetAlbum {

        @Test
        @DisplayName("참여자는 앨범장 닉네임과 함께 앨범을 조회한다")
        void returnsAlbumWithOwnerNickname() {
            // given
            Album album = AlbumFixture.album(10L, 1L);
            given(albumRepository.getById(10L)).willReturn(album);
            given(albumMemberRepository.getByAlbumIdAndUserId(10L, 2L))
                    .willReturn(AlbumFixture.member(album, 2L, AlbumRole.MEMBER));
            given(userQueryApi.getInfo(1L)).willReturn(new UserInfo(1L, "민수", "ROLE_USER"));

            // when
            AlbumDetailResult result = albumQueryService.getAlbum(2L, 10L);

            // then
            assertThat(result)
                    .isEqualTo(new AlbumDetailResult(10L, "제주 여행", 1L, "민수", album.getExpiresAt(), AlbumFixture.NOW));
        }

        @Test
        @DisplayName("참여자가 아니면 AlbumNotMemberException 을 던지고 사용자 정보는 조회하지 않는다")
        void throwsWhenNotMember() {
            // given
            given(albumRepository.getById(10L)).willReturn(AlbumFixture.album(10L, 1L));
            given(albumMemberRepository.getByAlbumIdAndUserId(10L, 3L)).willThrow(new AlbumNotMemberException());

            // when & then
            assertThatThrownBy(() -> albumQueryService.getAlbum(3L, 10L)).isInstanceOf(AlbumNotMemberException.class);
            then(userQueryApi).shouldHaveNoInteractions();
        }
    }

    @Test
    @DisplayName("내 앨범 목록은 참여 정보와 앨범을 합쳐 돌려준다")
    void getMyAlbums() {
        // given
        Album album = AlbumFixture.album(10L, 1L);
        given(albumMemberRepository.findAllByUserIdWithAlbum(1L))
                .willReturn(List.of(AlbumFixture.member(album, 1L, AlbumRole.OWNER)));

        // when
        List<AlbumResult> results = albumQueryService.getMyAlbums(1L);

        // then
        assertThat(results)
                .containsExactly(
                        new AlbumResult(10L, "제주 여행", AlbumRole.OWNER, album.getExpiresAt(), AlbumFixture.NOW));
    }
}
