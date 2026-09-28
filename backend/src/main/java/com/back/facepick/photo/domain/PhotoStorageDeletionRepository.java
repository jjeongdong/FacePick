package com.back.facepick.photo.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface PhotoStorageDeletionRepository {
    List<PhotoStorageDeletion> saveAll(List<PhotoStorageDeletion> deletions);

    /** 아직 지우지 않았고 createdBefore 이전(같은 시각 포함)에 기록된 행을 오래된 순으로 최대 limit 개. */
    List<PhotoStorageDeletion> findDue(LocalDateTime createdBefore, int limit);
}
