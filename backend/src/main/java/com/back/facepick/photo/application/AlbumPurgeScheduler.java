package com.back.facepick.photo.application;

import com.back.facepick.album.application.AlbumQueryApi;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

// PRD: 앨범은 생성 30일 뒤 삭제한다. 사진이 가장 무거운 데이터라 photo BC 가 정리를 주도하고,
// 앨범 행·얼굴 데이터는 마지막 조각의 AlbumPhotosPurgedEvent 로 각 BC 가 지운다 (다른 BC 에 직접 쓰지 않는다).
// 트랜잭션은 조각(AlbumPurgeService.purgeChunk)마다 따로라 이 메서드에는 두지 않는다.
@Slf4j
@Service
public class AlbumPurgeScheduler {
    private final AlbumQueryApi albumQueryApi;
    private final AlbumPurgeService albumPurgeService;
    private final int chunkSize;
    private final int albumsPerRun;

    public AlbumPurgeScheduler(
            AlbumQueryApi albumQueryApi,
            AlbumPurgeService albumPurgeService,
            @Value("${facepick.photo.album-purge.chunk-size}") int chunkSize,
            @Value("${facepick.photo.album-purge.albums-per-run}") int albumsPerRun) {
        this.albumQueryApi = albumQueryApi;
        this.albumPurgeService = albumPurgeService;
        this.chunkSize = chunkSize;
        this.albumsPerRun = albumsPerRun;
    }

    // 한 앨범이 실패해도(롤백된 조각만 남는다) 다음 앨범은 계속 지우고, 실패한 앨범은 다음 실행에서 남은 사진부터 이어서 한다.
    @Scheduled(fixedDelayString = "${facepick.photo.album-purge.interval-millis}")
    public void purge() {
        for (Long albumId : albumQueryApi.findExpiredAlbumIds(LocalDateTime.now(), albumsPerRun)) {
            try {
                int chunks = 0;
                while (albumPurgeService.purgeChunk(albumId, chunkSize)) {
                    chunks++;
                }
                log.info("만료 앨범을 삭제했다 albumId={}, chunks={}", albumId, chunks);
            } catch (RuntimeException e) {
                log.warn("만료 앨범 삭제 실패, 다음 실행에서 이어서 한다 albumId={}", albumId, e);
            }
        }
    }
}
