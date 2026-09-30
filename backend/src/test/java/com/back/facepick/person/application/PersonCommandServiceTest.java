package com.back.facepick.person.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

import com.back.facepick.person.domain.PersonFaceRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonCommandServiceTest {

    private static final Long ALBUM_ID = 10L;
    private static final List<Long> PHOTO_IDS = List.of(1L, 2L);

    @Mock
    private PersonFaceRepository personFaceRepository;

    @InjectMocks
    private PersonCommandService personCommandService;

    @Nested
    @DisplayName("사진 얼굴 삭제")
    class DeletePhotoFaces {

        @Test
        @DisplayName("앨범을 먼저 잠근 뒤 얼굴·분석 표시를 지우고 영향받은 인물을 정리한다")
        void locksThenDeletesThenCleansUp() {
            // given
            given(personFaceRepository.findPersonIdsOfPhotos(ALBUM_ID, PHOTO_IDS))
                    .willReturn(List.of(5L, 6L));

            // when
            personCommandService.deletePhotoFaces(ALBUM_ID, PHOTO_IDS);

            // then
            InOrder order = inOrder(personFaceRepository);
            order.verify(personFaceRepository).lockAlbum(ALBUM_ID);
            order.verify(personFaceRepository).findPersonIdsOfPhotos(ALBUM_ID, PHOTO_IDS);
            order.verify(personFaceRepository).deleteFacesOfPhotos(ALBUM_ID, PHOTO_IDS);
            order.verify(personFaceRepository).deleteAnalysesOfPhotos(ALBUM_ID, PHOTO_IDS);
            order.verify(personFaceRepository).cleanUpPersons(eq(List.of(5L, 6L)), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("얼굴이 없는 사진이면 분석 표시만 지우고 인물 정리는 건너뛴다")
        void skipsCleanUpWhenNoFaces() {
            // given
            given(personFaceRepository.findPersonIdsOfPhotos(ALBUM_ID, PHOTO_IDS))
                    .willReturn(List.of());

            // when
            personCommandService.deletePhotoFaces(ALBUM_ID, PHOTO_IDS);

            // then
            then(personFaceRepository).should().deleteAnalysesOfPhotos(ALBUM_ID, PHOTO_IDS);
            then(personFaceRepository).should(never()).cleanUpPersons(any(), any());
        }

        @Test
        @DisplayName("사진 ID 가 비어 있으면 잠그지도 지우지도 않는다 (IN () SQL 오류 방지)")
        void doesNothingForEmptyPhotoIds() {
            // when
            personCommandService.deletePhotoFaces(ALBUM_ID, List.of());

            // then
            then(personFaceRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("앨범 얼굴 데이터 파기")
    class PurgeAlbum {

        @Test
        @DisplayName("앨범을 먼저 잠근 뒤 그 앨범의 얼굴 데이터를 모두 지운다")
        void locksThenDeletesAll() {
            // when
            personCommandService.purgeAlbum(ALBUM_ID);

            // then
            InOrder order = inOrder(personFaceRepository);
            order.verify(personFaceRepository).lockAlbum(ALBUM_ID);
            order.verify(personFaceRepository).deleteAllOfAlbum(ALBUM_ID);
        }
    }
}
