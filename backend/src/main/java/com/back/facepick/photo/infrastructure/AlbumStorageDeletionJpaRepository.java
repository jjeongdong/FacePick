package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.AlbumStorageDeletion;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlbumStorageDeletionJpaRepository extends JpaRepository<AlbumStorageDeletion, Long> {

    // 네이티브 사유: ON CONFLICT DO NOTHING 으로 여러 번(서버 두 대가 함께) 돌아도 앨범당 한 행만 만든다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            nativeQuery = true,
            value =
                    """
                    INSERT INTO album_storage_deletions (album_id, created_at)
                    VALUES (:albumId, :now)
                    ON CONFLICT (album_id) DO NOTHING
                    """)
    int insertIfAbsent(@Param("albumId") Long albumId, @Param("now") LocalDateTime now);

    List<AlbumStorageDeletion> findByDeletedAtIsNullAndCreatedAtLessThanEqualOrderByCreatedAtAscAlbumIdAsc(
            LocalDateTime createdBefore, Limit limit);
}
