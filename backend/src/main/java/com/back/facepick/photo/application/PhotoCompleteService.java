package com.back.facepick.photo.application;

import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.domain.PhotoPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 동기 방식은 워커가 커밋된 행을 읽어야 하고 실패를 호출자에게 오류로 알려야 해서,
// 완료 트랜잭션을 끝낸 뒤 같은 요청 안에서 처리를 넘긴다.
// @TransactionalEventListener(AFTER_COMMIT) 는 예외가 호출자에게 전달되지 않아 쓰지 않는다.
@Service
@RequiredArgsConstructor
public class PhotoCompleteService {
    private final PhotoCommandService photoCommandService;
    private final PhotoPipeline photoPipeline;

    public PhotoCompleteResult completePhoto(Long userId, Long photoId) {
        PhotoCompleteResult result = photoCommandService.completePhoto(userId, photoId);
        photoPipeline.afterCommit(result.photoId());
        return result;
    }
}
