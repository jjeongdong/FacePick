package com.back.facepick.album.application.dto.result;

import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumRole;
import java.time.LocalDateTime;

public record AlbumResult(Long albumId, String title, AlbumRole role, LocalDateTime expiresAt, LocalDateTime joinedAt) {

    public static AlbumResult from(AlbumMember member) {
        return new AlbumResult(
                member.getAlbum().getId(),
                member.getAlbum().getTitle(),
                member.getRole(),
                member.getAlbum().getExpiresAt(),
                member.getCreatedAt());
    }
}
