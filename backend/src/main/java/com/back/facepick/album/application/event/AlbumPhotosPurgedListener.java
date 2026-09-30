package com.back.facepick.album.application.event;

import com.back.facepick.album.application.AlbumCommandService;
import com.back.facepick.photo.domain.event.AlbumPhotosPurgedEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AlbumPhotosPurgedListener {

    private final AlbumCommandService albumCommandService;

    // 사진 정리의 마지막 트랜잭션에서 함께 지운다(BEFORE_COMMIT). 실패하면 스토리지 정리 행까지 함께 롤백돼 다음 실행에서 다시 한다.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(AlbumPhotosPurgedEvent event) {
        albumCommandService.deleteExpiredAlbum(event.albumId(), LocalDateTime.now());
    }
}
