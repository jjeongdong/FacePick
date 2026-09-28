package com.back.facepick.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;

import com.back.facepick.photo.domain.PhotoStorage;
import com.back.facepick.photo.domain.PhotoStorageDeletion;
import com.back.facepick.photo.domain.PhotoStorageDeletionRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoStorageCleanerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Mock
    private PhotoStorageDeletionRepository photoStorageDeletionRepository;

    @Mock
    private PhotoStorage photoStorage;

    private PhotoStorageCleaner photoStorageCleaner;

    @BeforeEach
    void setUp() {
        photoStorageCleaner = new PhotoStorageCleaner(photoStorageDeletionRepository, photoStorage, 20);
    }

    @Test
    @DisplayName("20분 전보다 오래된 행을 100개씩 가져와 파일 3개를 지우고 지운 시각을 기록한다")
    void deletesDueRows() {
        // given
        PhotoStorageDeletion deletion = PhotoStorageDeletion.create("albums/1/originals/" + HASH_A, T);
        given(photoStorageDeletionRepository.findDue(any(LocalDateTime.class), eq(100)))
                .willReturn(List.of(deletion));

        // when
        photoStorageCleaner.clean();

        // then
        then(photoStorage).should().deleteObjects(deletion.storageKeys());
        assertThat(deletion.getDeletedAt()).isNotNull();
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(photoStorageDeletionRepository).should().findDue(captor.capture(), eq(100));
        assertThat(captor.getValue()).isCloseTo(LocalDateTime.now().minusMinutes(20), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("한 행이 실패하면 그 행은 남기고 다음 행은 계속 지운다")
    void continuesAfterFailure() {
        // given
        PhotoStorageDeletion failing = PhotoStorageDeletion.create("albums/1/originals/" + HASH_A, T);
        PhotoStorageDeletion next = PhotoStorageDeletion.create("albums/1/originals/" + HASH_B, T);
        given(photoStorageDeletionRepository.findDue(any(LocalDateTime.class), eq(100)))
                .willReturn(List.of(failing, next));
        // 인자가 다른 호출마다 strict stubs 경고가 예외로 나지 않게, 첫 행의 키에서만 실패시킨다.
        willAnswer(invocation -> {
                    if (failing.storageKeys().equals(invocation.getArgument(0))) {
                        throw new IllegalStateException("storage down");
                    }
                    return null;
                })
                .given(photoStorage)
                .deleteObjects(anyList());

        // when
        photoStorageCleaner.clean();

        // then
        assertThat(failing.getDeletedAt()).isNull();
        assertThat(next.getDeletedAt()).isNotNull();
        then(photoStorage).should().deleteObjects(next.storageKeys());
    }

    @Test
    @DisplayName("지울 행이 없으면 스토리지를 부르지 않는다")
    void doesNothingWhenEmpty() {
        // given
        given(photoStorageDeletionRepository.findDue(any(LocalDateTime.class), eq(100)))
                .willReturn(List.of());

        // when
        photoStorageCleaner.clean();

        // then
        then(photoStorage).should(never()).deleteObjects(anyList());
    }
}
