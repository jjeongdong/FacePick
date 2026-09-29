package com.back.facepick.person.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// face-worker(Python)가 쓰는 faces·persons·face_analyses 를 사진 삭제에 맞춰 정리한다.
public interface PersonFaceRepository {

    // face-worker 와 같은 앨범 단위 잠금. 트랜잭션이 끝날 때 풀린다.
    void lockAlbum(Long albumId);

    // 이 앨범의 해당 사진들에 있는 얼굴의 인물 ID. 중복 없이 오름차순.
    List<Long> findPersonIdsOfPhotos(Long albumId, Collection<Long> photoIds);

    // 대표 얼굴이었던 얼굴이 지워지면 그 인물의 cover_face_id 는 DB 가 비운다 (V11).
    void deleteFacesOfPhotos(Long albumId, Collection<Long> photoIds);

    void deleteAnalysesOfPhotos(Long albumId, Collection<Long> photoIds);

    // 대표 얼굴이 빈 인물은 남은 얼굴 중 det_score 최고(동점이면 face_id 작은 것)로 채우고, 남은 얼굴이 없으면 지운다.
    void cleanUpPersons(Collection<Long> personIds, LocalDateTime now);
}
