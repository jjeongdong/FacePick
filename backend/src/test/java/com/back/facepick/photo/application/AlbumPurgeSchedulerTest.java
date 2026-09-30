package com.back.facepick.photo.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.back.facepick.album.application.AlbumQueryApi;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlbumPurgeSchedulerTest {

    @Mock
    private AlbumQueryApi albumQueryApi;

    @Mock
    private AlbumPurgeService albumPurgeService;

    private AlbumPurgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AlbumPurgeScheduler(albumQueryApi, albumPurgeService, 500, 10);
    }

    @Test
    @DisplayName("만료 앨범마다 조각 삭제를 false 가 나올 때까지 반복한다")
    void purgesEachAlbumUntilDone() {
        // given
        given(albumQueryApi.findExpiredAlbumIds(any(LocalDateTime.class), eq(10)))
                .willReturn(List.of(1L, 2L));
        given(albumPurgeService.purgeChunk(1L, 500)).willReturn(true, true, false);
        given(albumPurgeService.purgeChunk(2L, 500)).willReturn(false);

        // when
        scheduler.purge();

        // then
        then(albumPurgeService).should(times(3)).purgeChunk(1L, 500);
        then(albumPurgeService).should(times(1)).purgeChunk(2L, 500);
    }

    @Test
    @DisplayName("한 앨범이 실패해도 다음 앨범은 계속 지운다")
    void continuesAfterFailure() {
        // given
        given(albumQueryApi.findExpiredAlbumIds(any(LocalDateTime.class), eq(10)))
                .willReturn(List.of(1L, 2L));
        given(albumPurgeService.purgeChunk(1L, 500)).willReturn(true).willThrow(new IllegalStateException("db down"));
        given(albumPurgeService.purgeChunk(2L, 500)).willReturn(false);

        // when
        scheduler.purge();

        // then
        then(albumPurgeService).should(times(2)).purgeChunk(1L, 500);
        then(albumPurgeService).should(times(1)).purgeChunk(2L, 500);
    }
}
