package com.back.facepick.album.domain;

public interface AlbumRepository {
    Album save(Album album);

    Album getById(Long albumId);
}
