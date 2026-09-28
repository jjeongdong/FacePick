package com.back.facepick.photo.domain;

import java.util.List;

public interface PhotoOutboxRepository {
    PhotoOutbox save(PhotoOutbox outbox);

    /** 아직 발행하지 않은 행을 기록된 순서(outbox_id 오름차순)로 최대 limit 개. */
    List<PhotoOutbox> findUnpublished(int limit);
}
