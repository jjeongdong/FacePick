package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.PhotoStorageDeletion;
import com.back.facepick.photo.domain.PhotoStorageDeletionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PhotoStorageDeletionRepositoryImpl implements PhotoStorageDeletionRepository {
    private final PhotoStorageDeletionJpaRepository photoStorageDeletionJpaRepository;

    @Override
    public List<PhotoStorageDeletion> saveAll(List<PhotoStorageDeletion> deletions) {
        return photoStorageDeletionJpaRepository.saveAll(deletions);
    }

    @Override
    public List<PhotoStorageDeletion> findDue(LocalDateTime createdBefore, int limit) {
        return photoStorageDeletionJpaRepository.findByDeletedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
                createdBefore, Limit.of(limit));
    }
}
