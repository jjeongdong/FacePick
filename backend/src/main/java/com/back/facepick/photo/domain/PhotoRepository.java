package com.back.facepick.photo.domain;

import java.util.Collection;
import java.util.List;

public interface PhotoRepository {
    // 없으면 PhotoNotFoundException.
    Photo getById(Long photoId);

    List<Photo> saveAll(List<Photo> photos);

    List<Photo> findAllByAlbumIdAndContentHashes(Long albumId, Collection<String> contentHashes);
}
