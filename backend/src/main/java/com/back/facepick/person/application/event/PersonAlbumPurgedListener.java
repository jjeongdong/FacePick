package com.back.facepick.person.application.event;

import com.back.facepick.person.application.PersonCommandService;
import com.back.facepick.photo.domain.event.AlbumPhotosPurgedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PersonAlbumPurgedListener {

    private final PersonCommandService personCommandService;

    // 앨범 행 삭제와 같은 트랜잭션(BEFORE_COMMIT)이어야, 앨범은 사라졌는데 얼굴 데이터가 남는 상태가 생기지 않는다.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(AlbumPhotosPurgedEvent event) {
        personCommandService.purgeAlbum(event.albumId());
    }
}
