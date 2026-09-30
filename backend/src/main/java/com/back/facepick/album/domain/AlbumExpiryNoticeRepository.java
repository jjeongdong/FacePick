package com.back.facepick.album.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlbumExpiryNoticeRepository {
    AlbumExpiryNotice save(AlbumExpiryNotice notice);

    /** expires_at 이 (now, until] 인 앨범의 멤버마다 PENDING 행을 만든다. 이미 있는 (앨범, 멤버) 는 건너뛴다. 새로 만든 행 수. */
    int createPendingForAlbumsExpiringBetween(LocalDateTime now, LocalDateTime until);

    /** 보낼 때가 된 PENDING 행을 오래된 순으로 최대 limit 개 잠근다. 다른 트랜잭션이 잠근 행은 건너뛴다. 트랜잭션 안에서만. */
    List<AlbumExpiryNotice> findDueForUpdate(LocalDateTime now, int limit);

    Optional<AlbumExpiryNotice> findById(Long noticeId);
}
