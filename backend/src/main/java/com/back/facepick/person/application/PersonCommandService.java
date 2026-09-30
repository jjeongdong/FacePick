package com.back.facepick.person.application;

import com.back.facepick.person.domain.PersonFaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonCommandService {

    private final PersonFaceRepository personFaceRepository;

    // 사진 삭제와 한 트랜잭션이어야 사진은 지워졌는데 얼굴은 남는 상태가 생기지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePhotoFaces(Long albumId, List<Long> photoIds) {
        // 빈 목록이면 IN () 가 SQL 오류라 바로 끝낸다.
        if (photoIds.isEmpty()) {
            return;
        }
        // face-worker 가 같은 앨범에 저장하는 중이면 끝날 때까지 기다린다. 잠금을 얻은 뒤의 조회는 그 저장까지 본다.
        personFaceRepository.lockAlbum(albumId);
        List<Long> personIds = personFaceRepository.findPersonIdsOfPhotos(albumId, photoIds);
        personFaceRepository.deleteFacesOfPhotos(albumId, photoIds);
        personFaceRepository.deleteAnalysesOfPhotos(albumId, photoIds);
        if (!personIds.isEmpty()) {
            personFaceRepository.cleanUpPersons(personIds, LocalDateTime.now());
        }
    }

    // 앨범 자동 삭제의 마지막 트랜잭션에서 부른다. 조각 삭제에서 대부분 지워지므로 보통은 0행이고,
    // 경합으로 남은 얼굴까지 확실히 파기하기 위한 것이다 (PRD: 앨범 삭제 시 얼굴 임베딩 즉시 파기).
    @Transactional(propagation = Propagation.MANDATORY)
    public void purgeAlbum(Long albumId) {
        // face-worker 가 같은 앨범에 저장하는 중이면 끝날 때까지 기다린다.
        personFaceRepository.lockAlbum(albumId);
        personFaceRepository.deleteAllOfAlbum(albumId);
    }
}
