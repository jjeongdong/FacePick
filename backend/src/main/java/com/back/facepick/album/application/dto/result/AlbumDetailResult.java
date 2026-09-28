package com.back.facepick.album.application.dto.result;

import com.back.facepick.album.domain.Album;
import java.time.LocalDateTime;

public record AlbumDetailResult(
        Long albumId,
        String title,
        Long ownerId,
        String ownerNickname,
        LocalDateTime expiresAt,
        LocalDateTime createdAt) {

    public static AlbumDetailResult of(Album album, String ownerNickname) {
        return new AlbumDetailResult(
                album.getId(),
                album.getTitle(),
                album.getOwnerId(),
                ownerNickname,
                album.getExpiresAt(),
                album.getCreatedAt());
    }
}
