package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.AlbumExpiryNotice;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlbumExpiryNoticeJpaRepository extends JpaRepository<AlbumExpiryNotice, Long> {

    // 네이티브 사유: ON CONFLICT DO NOTHING 으로 여러 번(서버 두 대가 함께) 돌아도 멤버마다 한 행만 만든다.
    // albums·album_members·album_expiry_notices 모두 album BC 테이블이다.
    @Modifying(clearAutomatically = true)
    @Query(
            nativeQuery = true,
            value =
                    """
                    INSERT INTO album_expiry_notices
                        (album_id, user_id, status, attempts, next_attempt_at, created_at, modified_at)
                    SELECT m.album_id, m.user_id, 'PENDING', 0, :now, :now, :now
                    FROM albums a
                    JOIN album_members m ON m.album_id = a.album_id
                    WHERE a.expires_at > :now AND a.expires_at <= :until
                    ON CONFLICT (album_id, user_id) DO NOTHING
                    """)
    int insertPendingForAlbumsExpiringBetween(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    // 네이티브 사유: FOR UPDATE SKIP LOCKED — 여러 발송기가 동시에 돌아도 서로 다른 행을 가져간다.
    @Query(
            nativeQuery = true,
            value =
                    """
                    SELECT * FROM album_expiry_notices
                    WHERE status = 'PENDING' AND next_attempt_at <= :now
                    ORDER BY next_attempt_at, notice_id
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """)
    List<AlbumExpiryNotice> findDueForUpdateSkipLocked(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
