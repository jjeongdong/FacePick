package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.Album;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlbumJpaRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByInviteCode(String inviteCode);

    @Query("select a.id from Album a where a.expiresAt <= :now order by a.expiresAt, a.id")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now, Limit limit);

    // 벌크 삭제라 영속성 컨텍스트를 거치지 않는다. album_members·album_expiry_notices 는 FK ON DELETE CASCADE 로 DB 가 지운다.
    // 만료 조건은 잘못된 호출로 살아 있는 앨범을 지우지 않게 하는 안전장치다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Album a where a.id = :albumId and a.expiresAt <= :now")
    int deleteExpired(@Param("albumId") Long albumId, @Param("now") LocalDateTime now);
}
