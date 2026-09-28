package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoJpaRepository extends JpaRepository<Photo, Long> {
    List<Photo> findAllByAlbumIdAndContentHashIn(Long albumId, Collection<String> contentHashes);

    List<Photo> findByAlbumIdAndStatusOrderByUploadedAtDescIdDesc(Long albumId, PhotoStatus status, Limit limit);

    // (uploaded_at, photo_id) 가 커서보다 작은 행. 같은 완료 시각은 photo_id 로 이어 간다.
    @Query(
            """
            select p from Photo p
            where p.albumId = :albumId and p.status = :status
              and (p.uploadedAt < :uploadedAt or (p.uploadedAt = :uploadedAt and p.id < :photoId))
            order by p.uploadedAt desc, p.id desc
            """)
    List<Photo> findPageAfter(
            @Param("albumId") Long albumId,
            @Param("status") PhotoStatus status,
            @Param("uploadedAt") LocalDateTime uploadedAt,
            @Param("photoId") Long photoId,
            Limit limit);

    Optional<Photo> findByIdAndStatus(Long photoId, PhotoStatus status);
}
