package com.back.facepick.album.application.dto.result;

import com.back.facepick.album.domain.Album;
import java.time.LocalDateTime;

public record AlbumCreateResult(Long albumId, String title, LocalDateTime expiresAt, LocalDateTime createdAt) {
    public static AlbumCreateResult from(Album album) {
        return new AlbumCreateResult(album.getId(), album.getTitle(), album.getExpiresAt(), album.getCreatedAt());
    }
}
