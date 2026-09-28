package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.back.facepick.album.domain.exception.AlbumNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlbumRepositoryImplTest {

    @Mock
    private AlbumJpaRepository albumJpaRepository;

    @InjectMocks
    private AlbumRepositoryImpl albumRepository;

    @Test
    @DisplayName("앨범이 없으면 AlbumNotFoundException 을 던진다")
    void getByIdThrowsWhenMissing() {
        // given
        given(albumJpaRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> albumRepository.getById(99L)).isInstanceOf(AlbumNotFoundException.class);
    }
}
