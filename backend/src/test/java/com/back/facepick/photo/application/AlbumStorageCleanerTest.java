package com.back.facepick.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.back.facepick.photo.domain.AlbumStorageDeletion;
import com.back.facepick.photo.domain.AlbumStorageDeletionRepository;
import com.back.facepick.photo.domain.PhotoStorage;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AlbumStorageCleanerTest {

    @Mock
    private AlbumStorageDeletionRepository albumStorageDeletionRepository;

    @Mock
    private PhotoStorage photoStorage;

    private AlbumStorageCleaner cleaner;

    @BeforeEach
    void setUp() {
        // 트랜잭션 경계는 통합 테스트(AlbumStorageCleanerIntegrationTest)가 본다. 여기서는 흐름만 본다.
        cleaner = new AlbumStorageCleaner(
                albumStorageDeletionRepository, photoStorage, mock(PlatformTransactionManager.class), 20);
    }

    @Test
    @DisplayName("20분 전보다 오래된 행의 앨범 prefix 를 지우고 지운 시각을 기록한다")
    void deletesDuePrefixes() {
        // given
        AlbumStorageDeletion deletion = deletion(7L);
        given(albumStorageDeletionRepository.findDue(any(LocalDateTime.class), eq(10)))
                .willReturn(List.of(deletion));
        given(albumStorageDeletionRepository.findById(7L)).willReturn(Optional.of(deletion));

        // when
        cleaner.clean();

        // then
        then(photoStorage).should().deleteByPrefix("albums/7/");
        assertThat(deletion.getDeletedAt()).isNotNull();
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(albumStorageDeletionRepository).should().findDue(captor.capture(), eq(10));
        assertThat(captor.getValue()).isCloseTo(LocalDateTime.now().minusMinutes(20), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("한 앨범이 실패하면 그 행은 남기고 다음 앨범은 계속 지운다")
    void continuesAfterFailure() {
        // given
        AlbumStorageDeletion failing = deletion(7L);
        AlbumStorageDeletion next = deletion(8L);
        given(albumStorageDeletionRepository.findDue(any(LocalDateTime.class), eq(10)))
                .willReturn(List.of(failing, next));
        willThrow(new IllegalStateException("storage down")).given(photoStorage).deleteByPrefix("albums/7/");
        given(albumStorageDeletionRepository.findById(8L)).willReturn(Optional.of(next));

        // when
        cleaner.clean();

        // then
        assertThat(failing.getDeletedAt()).isNull();
        assertThat(next.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("지울 행이 없으면 스토리지를 부르지 않는다")
    void doesNothingWhenEmpty() {
        // given
        given(albumStorageDeletionRepository.findDue(any(LocalDateTime.class), eq(10)))
                .willReturn(List.of());

        // when
        cleaner.clean();

        // then
        then(photoStorage).should(never()).deleteByPrefix(anyString());
    }

    private static AlbumStorageDeletion deletion(Long albumId) {
        AlbumStorageDeletion deletion = BeanUtils.instantiateClass(AlbumStorageDeletion.class);
        ReflectionTestUtils.setField(deletion, "albumId", albumId);
        return deletion;
    }
}
