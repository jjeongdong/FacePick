package com.back.facepick.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.back.facepick.album.application.AlbumQueryApi;
import com.back.facepick.album.application.dto.api.AlbumInfo;
import com.back.facepick.global.response.CursorPageResult;
import com.back.facepick.photo.application.dto.result.PhotoDetailResult;
import com.back.facepick.photo.application.dto.result.PhotoSummaryResult;
import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoCursor;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.PhotoStorage;
import com.back.facepick.photo.domain.exception.PhotoViewAlbumExpiredException;
import com.back.facepick.photo.domain.exception.PhotoViewNotAlbumMemberException;
import com.back.facepick.photo.fixture.PhotoFixture;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ALBUM_ID = 10L;
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2999, 1, 1, 0, 0);
    private static final LocalDateTime PAST = LocalDateTime.of(2000, 1, 1, 0, 0);

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private PhotoStorage photoStorage;

    @Mock
    private AlbumQueryApi albumQueryApi;

    @InjectMocks
    private PhotoQueryService photoQueryService;

    @Nested
    @DisplayName("사진 목록")
    class GetPhotos {

        @Test
        @DisplayName("size 보다 많으면 size 만큼 주고 마지막 사진을 다음 커서로 준다")
        void returnsPageWithNextCursor() {
            // given
            allowView(ALBUM_ID);
            Photo third = PhotoFixture.processed(3L, ALBUM_ID, USER_ID, hash(3));
            Photo second = PhotoFixture.processed(2L, ALBUM_ID, USER_ID, hash(2));
            Photo first = PhotoFixture.processed(1L, ALBUM_ID, USER_ID, hash(1));
            given(photoRepository.findUploadedByAlbumId(ALBUM_ID, 3)).willReturn(List.of(third, second, first));
            stubUrls();

            // when
            CursorPageResult<PhotoSummaryResult> result = photoQueryService.getPhotos(USER_ID, ALBUM_ID, null, 2);

            // then
            assertThat(result.content()).extracting(PhotoSummaryResult::photoId).containsExactly(3L, 2L);
            assertThat(result.hasNext()).isTrue();
            assertThat(PhotoCursorCodec.decode(result.nextCursor())).isEqualTo(PhotoCursor.from(second));
            assertThat(result.content().getFirst().thumbnailUrl())
                    .isEqualTo("http://storage/albums/10/thumbnails/" + hash(3) + ".jpg");
            assertThat(result.content().getFirst().urlExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("마지막 페이지가 정확히 size 장이면 hasNext false, nextCursor null")
        void lastPageExactlySize() {
            // given
            allowView(ALBUM_ID);
            given(photoRepository.findUploadedByAlbumId(ALBUM_ID, 3))
                    .willReturn(List.of(
                            PhotoFixture.processed(2L, ALBUM_ID, USER_ID, hash(2)),
                            PhotoFixture.processed(1L, ALBUM_ID, USER_ID, hash(1))));
            stubUrls();

            // when
            CursorPageResult<PhotoSummaryResult> result = photoQueryService.getPhotos(USER_ID, ALBUM_ID, null, 2);

            // then
            assertThat(result.content()).hasSize(2);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("커서가 있으면 그 뒤부터 조회한다")
        void continuesAfterCursor() {
            // given
            allowView(ALBUM_ID);
            PhotoCursor cursor = new PhotoCursor(LocalDateTime.of(2026, 9, 1, 12, 0), 5L);
            given(photoRepository.findUploadedByAlbumIdAfter(ALBUM_ID, cursor, 21))
                    .willReturn(List.of());
            given(photoStorage.downloadUrlExpiry()).willReturn(Duration.ofMinutes(60));

            // when
            CursorPageResult<PhotoSummaryResult> result =
                    photoQueryService.getPhotos(USER_ID, ALBUM_ID, PhotoCursorCodec.encode(cursor), 20);

            // then
            assertThat(result.content()).isEmpty();
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("빈 문자열 커서는 첫 페이지로 본다")
        void blankCursorMeansFirstPage() {
            // given
            allowView(ALBUM_ID);
            given(photoRepository.findUploadedByAlbumId(ALBUM_ID, 21)).willReturn(List.of());
            given(photoStorage.downloadUrlExpiry()).willReturn(Duration.ofMinutes(60));

            // when
            photoQueryService.getPhotos(USER_ID, ALBUM_ID, "", 20);

            // then
            then(photoRepository).should().findUploadedByAlbumId(ALBUM_ID, 21);
        }

        @Test
        @DisplayName("워커 처리 전 사진은 썸네일 URL 없이 준다")
        void unprocessedPhotoHasNoThumbnail() {
            // given
            allowView(ALBUM_ID);
            given(photoRepository.findUploadedByAlbumId(ALBUM_ID, 21))
                    .willReturn(List.of(PhotoFixture.uploaded(1L, ALBUM_ID, USER_ID, hash(1))));
            given(photoStorage.downloadUrlExpiry()).willReturn(Duration.ofMinutes(60));

            // when
            CursorPageResult<PhotoSummaryResult> result = photoQueryService.getPhotos(USER_ID, ALBUM_ID, null, 20);

            // then
            PhotoSummaryResult summary = result.content().getFirst();
            assertThat(summary.thumbnailUrl()).isNull();
            assertThat(summary.width()).isNull();
            then(photoStorage).should(never()).createDownloadUrl(anyString());
        }

        @Test
        @DisplayName("참여자가 아니면 PhotoViewNotAlbumMemberException, 사진을 조회하지 않는다")
        void rejectsNonMember() {
            // given
            given(albumQueryApi.getInfo(ALBUM_ID)).willReturn(new AlbumInfo(ALBUM_ID, FAR_FUTURE));
            given(albumQueryApi.isMember(ALBUM_ID, USER_ID)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> photoQueryService.getPhotos(USER_ID, ALBUM_ID, null, 20))
                    .isInstanceOf(PhotoViewNotAlbumMemberException.class);
            then(photoRepository).should(never()).findUploadedByAlbumId(anyLong(), anyInt());
        }

        @Test
        @DisplayName("만료된 앨범이면 PhotoViewAlbumExpiredException")
        void rejectsExpiredAlbum() {
            // given
            given(albumQueryApi.getInfo(ALBUM_ID)).willReturn(new AlbumInfo(ALBUM_ID, PAST));
            given(albumQueryApi.isMember(ALBUM_ID, USER_ID)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> photoQueryService.getPhotos(USER_ID, ALBUM_ID, null, 20))
                    .isInstanceOf(PhotoViewAlbumExpiredException.class);
        }
    }

    @Nested
    @DisplayName("사진 단건")
    class GetPhoto {

        @Test
        @DisplayName("미리보기 URL 과 첨부 파일명을 넣은 원본 URL 을 준다")
        void returnsPreviewAndOriginal() throws Exception {
            // given
            Photo photo = PhotoFixture.processed(12L, ALBUM_ID, USER_ID, hash(1));
            given(photoRepository.getUploadedById(12L)).willReturn(photo);
            allowView(ALBUM_ID);
            stubUrls();
            given(photoStorage.createDownloadUrl(photo.getStorageKey(), "facepick-12.jpg"))
                    .willReturn(URI.create("http://storage/original?name=facepick-12.jpg")
                            .toURL());

            // when
            PhotoDetailResult result = photoQueryService.getPhoto(USER_ID, 12L);

            // then
            assertThat(result.photoId()).isEqualTo(12L);
            assertThat(result.albumId()).isEqualTo(ALBUM_ID);
            assertThat(result.contentType()).isEqualTo("image/jpeg");
            assertThat(result.width()).isEqualTo(4032);
            assertThat(result.previewUrl()).isEqualTo("http://storage/albums/10/previews/" + hash(1) + ".jpg");
            assertThat(result.originalUrl()).isEqualTo("http://storage/original?name=facepick-12.jpg");
        }

        @Test
        @DisplayName("워커 처리 전이면 미리보기 URL 은 없고 원본 URL 은 있다")
        void unprocessedPhotoHasOnlyOriginal() throws Exception {
            // given
            Photo photo = PhotoFixture.uploaded(12L, ALBUM_ID, USER_ID, hash(1));
            given(photoRepository.getUploadedById(12L)).willReturn(photo);
            allowView(ALBUM_ID);
            given(photoStorage.downloadUrlExpiry()).willReturn(Duration.ofMinutes(60));
            given(photoStorage.createDownloadUrl(photo.getStorageKey(), "facepick-12.jpg"))
                    .willReturn(URI.create("http://storage/original").toURL());

            // when
            PhotoDetailResult result = photoQueryService.getPhoto(USER_ID, 12L);

            // then
            assertThat(result.previewUrl()).isNull();
            assertThat(result.originalUrl()).isEqualTo("http://storage/original");
        }

        @Test
        @DisplayName("사진이 속한 앨범의 참여자가 아니면 PhotoViewNotAlbumMemberException")
        void checksPhotosOwnAlbum() {
            // given
            Long otherAlbumId = 99L;
            given(photoRepository.getUploadedById(12L))
                    .willReturn(PhotoFixture.processed(12L, otherAlbumId, 2L, hash(1)));
            given(albumQueryApi.getInfo(otherAlbumId)).willReturn(new AlbumInfo(otherAlbumId, FAR_FUTURE));
            given(albumQueryApi.isMember(otherAlbumId, USER_ID)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> photoQueryService.getPhoto(USER_ID, 12L))
                    .isInstanceOf(PhotoViewNotAlbumMemberException.class);
            then(photoStorage).should(never()).createDownloadUrl(anyString(), anyString());
        }
    }

    private void allowView(Long albumId) {
        given(albumQueryApi.getInfo(albumId)).willReturn(new AlbumInfo(albumId, FAR_FUTURE));
        given(albumQueryApi.isMember(albumId, USER_ID)).willReturn(true);
    }

    // 키를 그대로 URL 에 담아 어떤 파일을 서명했는지 응답에서 확인한다.
    private void stubUrls() {
        given(photoStorage.downloadUrlExpiry()).willReturn(Duration.ofMinutes(60));
        given(photoStorage.createDownloadUrl(anyString()))
                .willAnswer(invocation -> URI.create("http://storage/" + invocation.getArgument(0))
                        .toURL());
    }

    private static String hash(int number) {
        return String.format("%064x", number);
    }
}
