package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.AlbumMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlbumMemberJpaRepository extends JpaRepository<AlbumMember, Long> {

    @Query("select m from AlbumMember m where m.album.id = :albumId and m.userId = :userId")
    Optional<AlbumMember> findByAlbumIdAndUserId(@Param("albumId") Long albumId, @Param("userId") Long userId);

    // 목록에서 앨범 제목·만료일을 함께 쓰므로 fetch join 으로 N+1 을 막는다.
    @Query("select m from AlbumMember m join fetch m.album where m.userId = :userId"
            + " order by m.createdAt desc, m.id desc")
    List<AlbumMember> findAllByUserIdWithAlbum(@Param("userId") Long userId);
}
