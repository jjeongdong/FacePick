package com.back.facepick.photo.application;

import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.PhotoStorage;
import com.back.facepick.photo.domain.PhotoStorageDeletion;
import com.back.facepick.photo.domain.PhotoStorageDeletionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Transactional 은 application 에만 둘 수 있고 infrastructure 는 application 을 부를 수 없어 스케줄러를 여기에 둔다.
@Slf4j
@Service
public class PhotoStorageCleaner {
    private static final int BATCH_SIZE = 100;

    private final PhotoStorageDeletionRepository photoStorageDeletionRepository;
    private final PhotoRepository photoRepository;
    private final PhotoStorage photoStorage;
    private final Duration deletionDelay;

    public PhotoStorageCleaner(
            PhotoStorageDeletionRepository photoStorageDeletionRepository,
            PhotoRepository photoRepository,
            PhotoStorage photoStorage,
            @Value("${storage.deletion-delay-minutes}") long deletionDelayMinutes) {
        this.photoStorageDeletionRepository = photoStorageDeletionRepository;
        this.photoRepository = photoRepository;
        this.photoStorage = photoStorage;
        this.deletionDelay = Duration.ofMinutes(deletionDelayMinutes);
    }

    // 늦게 지우는 이유: 지운 PENDING 사진에 업로드 URL(15분)로 늦게 PUT 된 원본과,
    // 처리 중이던 썸네일 워커가 뒤늦게 올린 파일까지 함께 지우기 위해서다.
    // 파일 삭제는 순서가 필요 없어, 한 행이 실패해도 다음 행을 계속 지운다 (실패한 행은 다음 주기에 다시).
    @Scheduled(fixedDelayString = "${storage.deletion-interval-millis}")
    @Transactional
    public void clean() {
        LocalDateTime now = LocalDateTime.now();
        for (PhotoStorageDeletion deletion :
                photoStorageDeletionRepository.findDue(now.minus(deletionDelay), BATCH_SIZE)) {
            // 저장 키는 파일 내용 해시로 정해져서, 지운 뒤 같은 파일을 다시 올리면 새 사진이 같은 키를 쓴다.
            // 그 파일은 새 사진의 것이므로 지우지 않는다 (지우면 새 사진이 목록에만 남고 모든 URL 이 404 가 된다).
            if (photoRepository.existsByStorageKey(deletion.getStorageKey())) {
                deletion.markDeleted(now);
                continue;
            }
            try {
                photoStorage.deleteObjects(deletion.storageKeys());
            } catch (RuntimeException e) {
                log.error("스토리지 파일 삭제 실패, 다음 주기에 다시 지운다 deletionId={}", deletion.getId(), e);
                continue;
            }
            deletion.markDeleted(now);
        }
    }
}
