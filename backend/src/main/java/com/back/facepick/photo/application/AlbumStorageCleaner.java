package com.back.facepick.photo.application;

import com.back.facepick.photo.domain.AlbumStorageDeletion;
import com.back.facepick.photo.domain.AlbumStorageDeletionRepository;
import com.back.facepick.photo.domain.PhotoStorage;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 삭제된 만료 앨범의 스토리지 파일을 앨범 prefix 로 통째로 지운다 (PhotoStorageCleaner 와 같은 지연·재시도 방식).
@Slf4j
@Service
public class AlbumStorageCleaner {
    private static final int BATCH_SIZE = 10;

    private final AlbumStorageDeletionRepository albumStorageDeletionRepository;
    private final PhotoStorage photoStorage;
    private final Duration deletionDelay;

    public AlbumStorageCleaner(
            AlbumStorageDeletionRepository albumStorageDeletionRepository,
            PhotoStorage photoStorage,
            @Value("${storage.deletion-delay-minutes}") long deletionDelayMinutes) {
        this.albumStorageDeletionRepository = albumStorageDeletionRepository;
        this.photoStorage = photoStorage;
        this.deletionDelay = Duration.ofMinutes(deletionDelayMinutes);
    }

    // 늦게 지우는 이유: 만료 직전 받은 업로드 URL(15분)로 늦게 PUT 된 원본과,
    // 처리 중이던 썸네일 워커가 뒤늦게 올린 파일까지 함께 지우기 위해서다.
    // 한 앨범이 실패해도 다음 앨범을 계속 지운다 (실패한 행은 다음 주기에 다시).
    @Scheduled(fixedDelayString = "${storage.deletion-interval-millis}")
    @Transactional
    public void clean() {
        LocalDateTime now = LocalDateTime.now();
        for (AlbumStorageDeletion deletion :
                albumStorageDeletionRepository.findDue(now.minus(deletionDelay), BATCH_SIZE)) {
            try {
                photoStorage.deleteByPrefix(deletion.storagePrefix());
            } catch (RuntimeException e) {
                log.error("앨범 스토리지 삭제 실패, 다음 주기에 다시 지운다 albumId={}", deletion.getAlbumId(), e);
                continue;
            }
            deletion.markDeleted(now);
        }
    }
}
