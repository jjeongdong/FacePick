package com.back.facepick.person.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.back.facepick.person.application.dto.api.PhotoFaceInfo;
import com.back.facepick.person.domain.PersonFaceRepository;
import com.back.facepick.person.domain.PhotoFaceAnalysis;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonQueryApiTest {

    @Mock
    private PersonFaceRepository personFaceRepository;

    @InjectMocks
    private PersonQueryApi personQueryApi;

    @Test
    @DisplayName("사진 얼굴 조회 - 분석 결과를 Info 로 준다")
    void findsPhotoFace() {
        // given
        given(personFaceRepository.findPhotoFaceAnalysis(10L)).willReturn(Optional.of(new PhotoFaceAnalysis(1, 7L)));

        // when & then
        assertThat(personQueryApi.findPhotoFace(10L)).contains(new PhotoFaceInfo(1, 7L));
    }

    @Test
    @DisplayName("인물 사진 조회 - 저장소 결과를 그대로 준다")
    void getsPhotoIdsOfPerson() {
        // given
        given(personFaceRepository.findPhotoIdsOfPerson(1L, 7L)).willReturn(List.of(10L, 11L));

        // when & then
        assertThat(personQueryApi.getPhotoIdsOfPerson(1L, 7L)).containsExactly(10L, 11L);
    }
}
