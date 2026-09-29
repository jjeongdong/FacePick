package com.back.facepick.person.application.event;

import com.back.facepick.person.application.PersonCommandService;
import com.back.facepick.photo.domain.event.PhotosDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PersonPhotosDeletedListener {

    private final PersonCommandService personCommandService;

    // 기본(AFTER_COMMIT + REQUIRES_NEW)이면 정리가 실패해도 사진 삭제는 이미 커밋돼 주인 없는 얼굴이 남는다.
    // 사진 삭제와 함께 롤백되도록 같은 트랜잭션(BEFORE_COMMIT)에서 처리한다. 커밋 직전이라 앨범 잠금을 쥐는 시간도 짧다.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(PhotosDeletedEvent event) {
        personCommandService.deletePhotoFaces(event.albumId(), event.photoIds());
    }
}
