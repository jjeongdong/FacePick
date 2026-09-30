package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.AlbumStorageDeletion;
import com.back.facepick.photo.domain.AlbumStorageDeletionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AlbumStorageDeletionRepositoryImpl implements AlbumStorageDeletionRepository {
    private final AlbumStorageDeletionJpaRepository albumStorageDeletionJpaRepository;

    @Override
    public void saveIfAbsent(Long albumId, LocalDateTime now) {
        albumStorageDeletionJpaRepository.insertIfAbsent(albumId, now);
    }

    @Override
    public List<AlbumStorageDeletion> findDue(LocalDateTime createdBefore, int limit) {
        return albumStorageDeletionJpaRepository
                .findByDeletedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAscAlbumIdAsc(
                        createdBefore, Limit.of(limit));
    }

    @Override
    public Optional<AlbumStorageDeletion> findById(Long albumId) {
        return albumStorageDeletionJpaRepository.findById(albumId);
    }
}
