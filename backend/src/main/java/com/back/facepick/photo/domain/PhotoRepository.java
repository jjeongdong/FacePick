package com.back.facepick.photo.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository {
    // 없으면 PhotoNotFoundException.
    Photo getById(Long photoId);

    List<Photo> saveAll(List<Photo> photos);

    // 앨범 사진만. 셀피는 "같은 파일 한 번만" 규칙 밖이라 중복 검사에 넣지 않는다.
    List<Photo> findAllByAlbumIdAndContentHashes(Long albumId, Collection<String> contentHashes);

    // 업로드 완료 앨범 사진을 완료 시각 최신 순으로. 첫 페이지.
    List<Photo> findUploadedByAlbumId(Long albumId, int limit);

    // cursor 뒤의 업로드 완료 앨범 사진. 정렬은 findUploadedByAlbumId 와 같다.
    List<Photo> findUploadedByAlbumIdAfter(Long albumId, PhotoCursor cursor, int limit);

    // 없거나, 아직 업로드 전(PENDING)이거나, 셀피면 PhotoNotFoundException.
    Photo getUploadedById(Long photoId);

    // 이 앨범의 앨범 사진만, 상태와 관계없이. 없는 ID·다른 앨범 사진·셀피는 결과에서 빠진다.
    List<Photo> findAllByAlbumIdAndIds(Long albumId, Collection<Long> photoIds);

    // 이 앨범의 업로드 완료 앨범 사진만, photo_id 오름차순. 없는 ID·PENDING·다른 앨범 사진·셀피는 빠진다. 잠그지 않는다.
    List<Photo> findUploadedByAlbumIdAndIds(Long albumId, Collection<Long> photoIds);

    void deleteAll(List<Photo> photos);

    // 같은 저장 키(같은 앨범·같은 파일)를 쓰는 사진이 있는지. 상태와 관계없다.
    boolean existsByStorageKey(String storageKey);

    // 멤버의 셀피. 상태와 관계없다.
    Optional<Photo> findSelfie(Long albumId, Long uploaderId);

    // 셀피 교체·취소용이라 행을 잠근다. 같은 멤버의 동시 요청이 서로의 삭제를 기다린다.
    Optional<Photo> findSelfieForUpdate(Long albumId, Long uploaderId);

    // photoIds 중 이 앨범의 업로드 완료 앨범 사진을 완료 시각 최신 순으로. 첫 페이지.
    List<Photo> findUploadedByAlbumIdIn(Long albumId, Collection<Long> photoIds, int limit);

    // cursor 뒤. 정렬은 findUploadedByAlbumIdIn 과 같다.
    List<Photo> findUploadedByAlbumIdInAfter(Long albumId, Collection<Long> photoIds, PhotoCursor cursor, int limit);

    // 같은 트랜잭션에서 같은 멤버의 새 셀피를 넣기 전에 DELETE 를 DB 에 보낸다.
    // Hibernate 는 INSERT 를 DELETE 보다 먼저 보내므로 그냥 지우면 멤버당 셀피 하나 인덱스에 걸린다.
    void deleteAndFlush(Photo photo);
}
