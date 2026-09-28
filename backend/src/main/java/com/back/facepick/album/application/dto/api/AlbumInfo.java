package com.back.facepick.album.application.dto.api;

import com.back.facepick.album.domain.Album;
import java.time.LocalDateTime;

public record AlbumInfo(Long albumId, Long ownerId, LocalDateTime expiresAt) {
    public static AlbumInfo from(Album album) {
        return new AlbumInfo(album.getId(), album.getOwnerId(), album.getExpiresAt());
    }
}
