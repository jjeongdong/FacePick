package com.back.facepick.album.domain;

public interface AlbumRepository {
    Album save(Album album);

    Album getById(Long albumId);

    // 코드에 맞는 앨범이 없으면 AlbumInviteNotFoundException.
    Album getByInviteCode(String inviteCode);
}
