package com.back.facepick.photo.domain;

import java.util.Collection;
import java.util.List;

public interface PhotoRepository {
    // 없으면 PhotoNotFoundException.
    Photo getById(Long photoId);

    List<Photo> saveAll(List<Photo> photos);

    List<Photo> findAllByAlbumIdAndContentHashes(Long albumId, Collection<String> contentHashes);

    // 업로드 완료 사진을 완료 시각 최신 순으로. 첫 페이지.
    List<Photo> findUploadedByAlbumId(Long albumId, int limit);

    // cursor 뒤의 업로드 완료 사진. 정렬은 findUploadedByAlbumId 와 같다.
    List<Photo> findUploadedByAlbumIdAfter(Long albumId, PhotoCursor cursor, int limit);

    // 없거나 아직 업로드 전(PENDING)이면 PhotoNotFoundException.
    Photo getUploadedById(Long photoId);
}
