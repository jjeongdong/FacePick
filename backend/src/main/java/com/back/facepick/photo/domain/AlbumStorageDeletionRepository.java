package com.back.facepick.photo.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface AlbumStorageDeletionRepository {
    /** 앨범당 한 행. 이미 있으면(재시도·두 서버 동시 실행) 아무것도 하지 않는다. */
    void saveIfAbsent(Long albumId, LocalDateTime now);

    /** 아직 지우지 않았고 createdBefore 이전(같은 시각 포함)에 기록된 행을 오래된 순으로 최대 limit 개. */
    List<AlbumStorageDeletion> findDue(LocalDateTime createdBefore, int limit);
}
