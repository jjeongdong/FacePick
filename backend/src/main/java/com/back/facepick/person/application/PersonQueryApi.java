package com.back.facepick.person.application;

import com.back.facepick.person.application.dto.api.PhotoFaceInfo;
import com.back.facepick.person.domain.PersonFaceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PersonQueryApi {

    private final PersonFaceRepository personFaceRepository;

    /**
     * 사진 한 장의 얼굴 분석 결과를 조회한다.
     *
     * @param photoId 사진 ID
     * @return 분석 전이면 empty. 분석이 끝났으면 얼굴 수와 인물 ID(얼굴이 없으면 null)
     */
    @Transactional(readOnly = true)
    public Optional<PhotoFaceInfo> findPhotoFace(Long photoId) {
        return personFaceRepository.findPhotoFaceAnalysis(photoId).map(PhotoFaceInfo::from);
    }

    /**
     * 앨범에서 그 인물의 얼굴이 있는 사진 ID 를 조회한다.
     *
     * @param albumId 앨범 ID
     * @param personId 인물 ID
     * @return 사진 ID, 중복 없이 오름차순. 없으면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<Long> getPhotoIdsOfPerson(Long albumId, Long personId) {
        return personFaceRepository.findPhotoIdsOfPerson(albumId, personId);
    }
}
