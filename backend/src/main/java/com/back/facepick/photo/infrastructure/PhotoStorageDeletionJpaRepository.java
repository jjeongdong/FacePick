package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.PhotoStorageDeletion;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoStorageDeletionJpaRepository extends JpaRepository<PhotoStorageDeletion, Long> {
    List<PhotoStorageDeletion> findByDeletedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAscIdAsc(
            LocalDateTime createdBefore, Limit limit);
}
