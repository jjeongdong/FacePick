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
        // face-worker 가 같은 앨범에 저장하는 중이면 끝날 때까지 기다린다. 잠금을 얻은 뒤의 조회는 그 저장까지 본다.
        personFaceRepository.lockAlbum(albumId);
        List<Long> personIds = personFaceRepository.findPersonIdsOfPhotos(albumId, photoIds);
        personFaceRepository.deleteFacesOfPhotos(albumId, photoIds);
        personFaceRepository.deleteAnalysesOfPhotos(albumId, photoIds);
        if (!personIds.isEmpty()) {
            personFaceRepository.cleanUpPersons(personIds, LocalDateTime.now());
        }
    }
}
