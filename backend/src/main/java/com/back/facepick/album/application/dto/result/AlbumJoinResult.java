package com.back.facepick.album.application.dto.result;

import com.back.facepick.album.domain.Album;

public record AlbumJoinResult(Long albumId, String title) {
    public static AlbumJoinResult from(Album album) {
        return new AlbumJoinResult(album.getId(), album.getTitle());
    }
}
