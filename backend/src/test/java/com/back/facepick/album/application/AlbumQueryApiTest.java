package com.back.facepick.album.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.back.facepick.album.domain.AlbumMemberRepository;
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
}
