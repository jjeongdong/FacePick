package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.Album;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumJpaRepository extends JpaRepository<Album, Long> {
    Optional<Album> findByInviteCode(String inviteCode);
}
