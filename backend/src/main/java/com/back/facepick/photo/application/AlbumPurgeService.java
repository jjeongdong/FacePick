package com.back.facepick.photo.application;

import com.back.facepick.photo.domain.AlbumStorageDeletionRepository;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.event.AlbumPhotosPurgedEvent;
import com.back.facepick.photo.domain.event.PhotosDeletedEvent;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 만료 앨범 자동 삭제의 한 걸음. 사진 10,000장 앨범도 행 잠금·앨범 잠금을 오래 쥐지 않게 조각마다 트랜잭션을 나눈다.
// 진행 상태를 따로 저장하지 않는다: 만료된 앨범 행이 남아 있으면 다음 호출이 남은 사진부터 이어서 지운다.
@Service
@RequiredArgsConstructor
public class AlbumPurgeService {
    private final PhotoRepository photoRepository;
    private final AlbumStorageDeletionRepository albumStorageDeletionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 만료 앨범의 사진을 chunkSize 장까지 지운다. 남은 사진이 없으면 앨범 정리를 마무리한다.
     *
     * @return 사진을 지웠으면 true(다시 부를 것), 마무리했으면 false
     */
    @Transactional
    public boolean purgeChunk(Long albumId, int chunkSize) {
        List<Long> photoIds = photoRepository.findIdsForPurge(albumId, chunkSize);
        if (photoIds.isEmpty()) {
            // 스토리지 파일은 사진 행마다가 아니라 앨범 prefix 로 한 번에 지운다 (끝내지 않은 업로드·늦은 썸네일까지).
            albumStorageDeletionRepository.saveIfAbsent(albumId, LocalDateTime.now());
            // album BC 가 앨범 행을, person BC 가 남은 얼굴 데이터를 같은 트랜잭션에서 지운다.
            eventPublisher.publishEvent(new AlbumPhotosPurgedEvent(albumId));
            return false;
        }
        photoRepository.deleteAllByIds(photoIds);
        // person BC 가 같은 트랜잭션에서 이 사진들의 얼굴 데이터를 지운다.
        eventPublisher.publishEvent(new PhotosDeletedEvent(albumId, photoIds));
        return true;
    }
}
