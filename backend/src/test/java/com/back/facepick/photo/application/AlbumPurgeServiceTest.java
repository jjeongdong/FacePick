package com.back.facepick.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.back.facepick.photo.domain.AlbumStorageDeletionRepository;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.event.AlbumPhotosPurgedEvent;
import com.back.facepick.photo.domain.event.PhotosDeletedEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AlbumPurgeServiceTest {

    private static final Long ALBUM_ID = 10L;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private AlbumStorageDeletionRepository albumStorageDeletionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlbumPurgeService albumPurgeService;

    @Test
    @DisplayName("사진이 있으면 조각만큼 지우고 얼굴 정리 이벤트를 발행한 뒤 true")
    void deletesChunk() {
        // given
        given(photoRepository.findIdsForPurge(ALBUM_ID, 500)).willReturn(List.of(1L, 2L));

        // when
        boolean more = albumPurgeService.purgeChunk(ALBUM_ID, 500);

        // then
        assertThat(more).isTrue();
        then(photoRepository).should().deleteAllByIds(List.of(1L, 2L));
        then(eventPublisher).should().publishEvent(new PhotosDeletedEvent(ALBUM_ID, List.of(1L, 2L)));
        then(albumStorageDeletionRepository).should(never()).saveIfAbsent(any(), any());
    }

    @Test
    @DisplayName("사진이 없으면 스토리지 정리를 예약하고 앨범 정리 이벤트를 발행한 뒤 false")
    void finishesAlbum() {
        // given
        given(photoRepository.findIdsForPurge(ALBUM_ID, 500)).willReturn(List.of());

        // when
        boolean more = albumPurgeService.purgeChunk(ALBUM_ID, 500);

        // then
        assertThat(more).isFalse();
        then(albumStorageDeletionRepository).should().saveIfAbsent(eq(ALBUM_ID), any(LocalDateTime.class));
        then(eventPublisher).should().publishEvent(new AlbumPhotosPurgedEvent(ALBUM_ID));
        then(photoRepository).should(never()).deleteAllByIds(anyCollection());
    }
}
