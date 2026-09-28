package com.back.facepick.album.application.dto.result;

import com.back.facepick.album.domain.Album;

public record AlbumInviteResult(String inviteCode) {
    public static AlbumInviteResult from(Album album) {
        return new AlbumInviteResult(album.getInviteCode());
    }
}
