package com.back.facepick.photo.application;

import com.back.facepick.global.config.scheduling.SchedulerNames;
import com.back.facepick.photo.domain.AlbumStorageDeletion;
import com.back.facepick.photo.domain.AlbumStorageDeletionRepository;
import com.back.facepick.photo.domain.PhotoStorage;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 삭제된 만료 앨범의 스토리지 파일을 앨범 prefix 로 통째로 지운다 (PhotoStorageCleaner 와 같은 지연·재시도 방식).
@Slf4j
@Service
public class AlbumStorageCleaner {
    private static final int BATCH_SIZE = 10;

    private final AlbumStorageDeletionRepository albumStorageDeletionRepository;
    private final PhotoStorage photoStorage;
    private final Duration deletionDelay;
    private final TransactionTemplate transactionTemplate;

    public AlbumStorageCleaner(
            AlbumStorageDeletionRepository albumStorageDeletionRepository,
            PhotoStorage photoStorage,
            PlatformTransactionManager transactionManager,
            @Value("${storage.deletion-delay-minutes}") long deletionDelayMinutes) {
        this.albumStorageDeletionRepository = albumStorageDeletionRepository;
        this.photoStorage = photoStorage;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.deletionDelay = Duration.ofMinutes(deletionDelayMinutes);
    }

    // 늦게 지우는 이유: 만료 직전 받은 업로드 URL(15분)로 늦게 PUT 된 원본과,
    // 처리 중이던 썸네일 워커가 뒤늦게 올린 파일까지 함께 지우기 위해서다.
    // 한 앨범이 실패해도 다음 앨범을 계속 지운다 (실패한 행은 다음 주기에 다시).
    // 앨범 하나에 파일이 수만 개라 삭제가 몇 분 걸릴 수 있다. 그동안 트랜잭션을 열어 두면 커넥션을 쥐고
    // DB 전체의 vacuum 을 막으므로, 스토리지 호출은 트랜잭션 밖에서 하고 지운 기록만 앨범마다 짧게 커밋한다.
    @Scheduled(fixedDelayString = "${storage.deletion-interval-millis}", scheduler = SchedulerNames.STORAGE)
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
            transactionTemplate.executeWithoutResult(status -> albumStorageDeletionRepository
                    .findById(deletion.getAlbumId())
                    .ifPresent(row -> row.markDeleted(now)));
        }
    }
}
