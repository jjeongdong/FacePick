package com.back.facepick.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.back.facepick.album.application.AlbumQueryApi;
import com.back.facepick.album.application.dto.api.AlbumInfo;
import com.back.facepick.photo.application.dto.command.PhotoDeleteCommand;
import com.back.facepick.photo.application.dto.command.PhotoUploadCommand;
import com.back.facepick.photo.application.dto.command.PhotoUploadCommand.UploadFile;
import com.back.facepick.photo.application.dto.result.PhotoCompleteResult;
import com.back.facepick.photo.application.dto.result.PhotoDeleteResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult.FileResult;
import com.back.facepick.photo.application.dto.result.PhotoUploadResult.Status;
import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoOutbox;
import com.back.facepick.photo.domain.PhotoOutboxRepository;
import com.back.facepick.photo.domain.PhotoRepository;
import com.back.facepick.photo.domain.PhotoStatus;
import com.back.facepick.photo.domain.PhotoStorage;
import com.back.facepick.photo.domain.PhotoStorageDeletion;
import com.back.facepick.photo.domain.PhotoStorageDeletionRepository;
import com.back.facepick.photo.domain.exception.PhotoAlbumExpiredException;
import com.back.facepick.photo.domain.exception.PhotoDeleteAlbumExpiredException;
import com.back.facepick.photo.domain.exception.PhotoDeleteNotAlbumMemberException;
import com.back.facepick.photo.domain.exception.PhotoFileMissingException;
import com.back.facepick.photo.domain.exception.PhotoNotAlbumMemberException;
import com.back.facepick.photo.domain.exception.PhotoNotDeletableException;
import com.back.facepick.photo.domain.exception.PhotoUnsupportedTypeException;
import com.back.facepick.photo.fixture.PhotoFixture;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PhotoCommandServiceTest {

    private static final Long ALBUM_ID = 10L;
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private PhotoStorage photoStorage;

    @Mock
    private AlbumQueryApi albumQueryApi;

    @Mock
    private PhotoOutboxRepository photoOutboxRepository;

    @Mock
    private PhotoStorageDeletionRepository photoStorageDeletionRepository;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().build();

    @InjectMocks
    private PhotoCommandService photoCommandService;

    @Nested
    @DisplayName("업로드 URL 발급")
    class CreatePhotoUploads {

        @Test
        @DisplayName("처음 보는 파일은 PENDING 사진을 만들고 서명 URL 을 준다")
        void createsPendingPhotoForNewFile() throws Exception {
            // given
            givenMemberOfOpenAlbum(1L);
            given(photoRepository.findAllByAlbumIdAndContentHashes(ALBUM_ID, List.of(HASH_A)))
                    .willReturn(List.of());
            givenSaveAllAssignsIds();
            givenUploadUrls();

            // when
            PhotoUploadResult result = photoCommandService.createPhotoUploads(1L, ALBUM_ID, command(file(HASH_A)));

            // then
            assertThat(result.files())
                    .containsExactly(
                            new FileResult(HASH_A, 100L, Status.UPLOAD_REQUIRED, "image/jpeg", uploadUrl(HASH_A)));
            then(photoRepository)
                    .should()
                    .saveAll(argThat(photos ->
                            photos.size() == 1 && photos.get(0).getUploaderId().equals(1L)));
        }

        @Test
        @DisplayName("이미 올라간 파일은 URL 없이 ALREADY_UPLOADED")
        void skipsUploadedFile() {
            // given
            givenMemberOfOpenAlbum(1L);
            given(photoRepository.findAllByAlbumIdAndContentHashes(ALBUM_ID, List.of(HASH_A)))
                    .willReturn(List.of(PhotoFixture.uploaded(7L, ALBUM_ID, 2L, HASH_A)));
            given(photoRepository.saveAll(List.of())).willReturn(List.of());
            given(photoStorage.uploadUrlExpiry()).willReturn(Duration.ofMinutes(15));

            // when
            PhotoUploadResult result = photoCommandService.createPhotoUploads(1L, ALBUM_ID, command(file(HASH_A)));

            // then
            assertThat(result.files())
                    .containsExactly(new FileResult(HASH_A, 7L, Status.ALREADY_UPLOADED, "image/jpeg", null));
            then(photoStorage).should(never()).createUploadUrl(any(), any());
        }

        @Test
        @DisplayName("PENDING 재발급 - 끊긴 파일은 같은 photoId 로 URL 을 다시 주고 업로더를 요청자로 바꾼다")
        void reissuesUrlForPendingFile() throws Exception {
            // given
            Photo pending = PhotoFixture.pending(7L, ALBUM_ID, 2L, HASH_A);
            givenMemberOfOpenAlbum(1L);
            given(photoRepository.findAllByAlbumIdAndContentHashes(ALBUM_ID, List.of(HASH_A)))
                    .willReturn(List.of(pending));
            given(photoRepository.saveAll(List.of())).willReturn(List.of());
            givenUploadUrls();

            // when
            PhotoUploadResult result = photoCommandService.createPhotoUploads(1L, ALBUM_ID, command(file(HASH_A)));

            // then
            assertThat(result.files())
                    .containsExactly(
                            new FileResult(HASH_A, 7L, Status.UPLOAD_REQUIRED, "image/jpeg", uploadUrl(HASH_A)));
            assertThat(pending.getUploaderId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("PENDING 재발급 - 다른 형식으로 선언해도 처음 등록된 형식으로 서명하고 그 형식을 알려준다")
        void signsWithRegisteredContentType() throws Exception {
            // given
            givenMemberOfOpenAlbum(1L);
            given(photoRepository.findAllByAlbumIdAndContentHashes(ALBUM_ID, List.of(HASH_A)))
                    .willReturn(List.of(PhotoFixture.pending(7L, ALBUM_ID, 2L, HASH_A)));
            givenUploadUrls();

            // when
            PhotoUploadResult result = photoCommandService.createPhotoUploads(
                    1L, ALBUM_ID, command(new UploadFile(HASH_A, PhotoFixture.BYTE_SIZE, "image/png")));

            // then
            assertThat(result.files())
                    .containsExactly(
                            new FileResult(HASH_A, 7L, Status.UPLOAD_REQUIRED, "image/jpeg", uploadUrl(HASH_A)));
        }

        @Test
        @DisplayName("이미 있는 해시라도 선언이 규칙에 어긋나면 요청 전체를 거절한다")
        void validatesDeclaredValuesOfExistingFiles() {
            // given
            givenMemberOfOpenAlbum(1L);
            given(photoRepository.findAllByAlbumIdAndContentHashes(ALBUM_ID, List.of(HASH_A)))
                    .willReturn(List.of(PhotoFixture.uploaded(7L, ALBUM_ID, 2L, HASH_A)));

            // when & then
            assertThatThrownBy(() -> photoCommandService.createPhotoUploads(
                            1L, ALBUM_ID, command(new UploadFile(HASH_A, PhotoFixture.BYTE_SIZE, "image/gif"))))
                    .isInstanceOf(PhotoUnsupportedTypeException.class);
            then(photoRepository).should(never()).saveAll(anyList());
        }

        @Test
        @DisplayName("요청 안 중복 - 같은 해시가 두 번 오면 사진은 하나만 만들고 두 항목에 같은 photoId")
        void createsOnePhotoForDuplicateHashes() throws Exception {
            // given
            givenMemberOfOpenAlbum(1L);
            given(photoRepository.findAllByAlbumIdAndContentHashes(ALBUM_ID, List.of(HASH_A, HASH_B)))
                    .willReturn(List.of());
            givenSaveAllAssignsIds();
            givenUploadUrls();

            // when
            PhotoUploadResult result = photoCommandService.createPhotoUploads(
                    1L, ALBUM_ID, command(file(HASH_A), file(HASH_B), file(HASH_A)));

            // then
            assertThat(result.files())
                    .extracting(FileResult::contentHash, FileResult::photoId)
                    .containsExactly(tuple(HASH_A, 100L), tuple(HASH_B, 101L), tuple(HASH_A, 100L));
            then(photoRepository).should().saveAll(argThat(photos -> photos.size() == 2));
        }

        @Test
        @DisplayName("참여자가 아니면 PhotoNotAlbumMemberException 이고 사진을 조회하지 않는다")
        void throwsWhenNotMember() {
            // given
            given(albumQueryApi.getInfo(ALBUM_ID))
                    .willReturn(new AlbumInfo(ALBUM_ID, 99L, LocalDateTime.now().plusDays(1)));
            given(albumQueryApi.isMember(ALBUM_ID, 3L)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> photoCommandService.createPhotoUploads(3L, ALBUM_ID, command(file(HASH_A))))
                    .isInstanceOf(PhotoNotAlbumMemberException.class);
            then(photoRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("만료된 앨범이면 PhotoAlbumExpiredException")
        void throwsWhenAlbumExpired() {
            // given
            given(albumQueryApi.getInfo(ALBUM_ID))
                    .willReturn(new AlbumInfo(ALBUM_ID, 99L, LocalDateTime.now().minusDays(1)));
            given(albumQueryApi.isMember(ALBUM_ID, 1L)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> photoCommandService.createPhotoUploads(1L, ALBUM_ID, command(file(HASH_A))))
                    .isInstanceOf(PhotoAlbumExpiredException.class);
            then(photoRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("파일 하나라도 형식이 안 맞으면 요청 전체를 거절하고 저장하지 않는다")
        void rejectsWholeRequestWhenOneFileInvalid() {
            // given
            givenMemberOfOpenAlbum(1L);
            given(photoRepository.findAllByAlbumIdAndContentHashes(ALBUM_ID, List.of(HASH_A, HASH_B)))
                    .willReturn(List.of());

            // when & then
            assertThatThrownBy(() -> photoCommandService.createPhotoUploads(
                            1L, ALBUM_ID, command(file(HASH_A), new UploadFile(HASH_B, 1000L, "video/quicktime"))))
                    .isInstanceOf(PhotoUnsupportedTypeException.class);
            then(photoRepository).should(never()).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("업로드 완료")
    class CompletePhoto {

        @Test
        @DisplayName("스토리지 크기가 맞으면 UPLOADED 로 바꾸고 photo.uploaded 를 outbox 에 기록한다")
        void completesAndWritesOutbox() {
            // given
            Photo photo = PhotoFixture.pending(12L, ALBUM_ID, 1L);
            given(photoRepository.getById(12L)).willReturn(photo);
            given(photoStorage.findObjectSize(photo.getStorageKey())).willReturn(Optional.of(PhotoFixture.BYTE_SIZE));

            // when
            PhotoCompleteResult result = photoCommandService.completePhoto(1L, 12L);

            // then
            assertThat(result).isEqualTo(new PhotoCompleteResult(12L, PhotoStatus.UPLOADED));
            ArgumentCaptor<PhotoOutbox> captor = ArgumentCaptor.forClass(PhotoOutbox.class);
            then(photoOutboxRepository).should().save(captor.capture());
            PhotoOutbox outbox = captor.getValue();
            assertThat(outbox.getTopic()).isEqualTo("photo.uploaded");
            assertThat(outbox.getMessageKey()).isEqualTo("10");
            JsonNode payload = jsonMapper.readTree(outbox.getPayload());
            assertThat(payload.get("photoId").asLong()).isEqualTo(12L);
            assertThat(payload.get("albumId").asLong()).isEqualTo(10L);
            assertThat(payload.get("storageKey").asString()).isEqualTo(photo.getStorageKey());
            assertThat(payload.get("contentType").asString()).isEqualTo("image/jpeg");
            assertThat(payload.get("byteSize").asLong()).isEqualTo(PhotoFixture.BYTE_SIZE);
        }

        @Test
        @DisplayName("이미 UPLOADED 면 outbox 를 다시 쓰지 않고 같은 결과")
        void doesNotWriteOutboxTwice() {
            // given
            Photo photo = PhotoFixture.uploaded(12L, ALBUM_ID, 1L, HASH_A);
            given(photoRepository.getById(12L)).willReturn(photo);
            given(photoStorage.findObjectSize(photo.getStorageKey())).willReturn(Optional.of(PhotoFixture.BYTE_SIZE));

            // when
            PhotoCompleteResult result = photoCommandService.completePhoto(1L, 12L);

            // then
            assertThat(result).isEqualTo(new PhotoCompleteResult(12L, PhotoStatus.UPLOADED));
            then(photoOutboxRepository).should(never()).save(any(PhotoOutbox.class));
        }

        @Test
        @DisplayName("스토리지에 파일이 없으면 PhotoFileMissingException 이고 outbox 를 쓰지 않는다")
        void throwsWhenFileMissing() {
            // given
            Photo photo = PhotoFixture.pending(12L, ALBUM_ID, 1L);
            given(photoRepository.getById(12L)).willReturn(photo);
            given(photoStorage.findObjectSize(photo.getStorageKey())).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> photoCommandService.completePhoto(1L, 12L))
                    .isInstanceOf(PhotoFileMissingException.class);
            then(photoOutboxRepository).should(never()).save(any(PhotoOutbox.class));
        }
    }

    @Nested
    @DisplayName("사진 삭제")
    class DeletePhotos {

        private static final Long OWNER_ID = 99L;

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("자기 사진을 지우고 파일 삭제 대기열에 원본 키를 남긴다")
        void deletesOwnPhotos() {
            // given
            givenAlbum(1L, true, LocalDateTime.now().plusDays(1));
            Photo first = PhotoFixture.uploaded(13L, ALBUM_ID, 1L, HASH_B);
            Photo second = PhotoFixture.pending(12L, ALBUM_ID, 1L, HASH_A);
            given(photoRepository.findAllByAlbumIdAndIds(ALBUM_ID, List.of(13L, 12L)))
                    .willReturn(List.of(first, second));

            // when
            PhotoDeleteResult result =
                    photoCommandService.deletePhotos(1L, ALBUM_ID, new PhotoDeleteCommand(List.of(13L, 12L)));

            // then
            assertThat(result.deletedPhotoIds()).containsExactly(12L, 13L);
            then(photoRepository).should().deleteAll(List.of(first, second));
            ArgumentCaptor<List<PhotoStorageDeletion>> captor = ArgumentCaptor.forClass(List.class);
            then(photoStorageDeletionRepository).should().saveAll(captor.capture());
            assertThat(captor.getValue())
                    .extracting(PhotoStorageDeletion::getStorageKey)
                    .containsExactly(first.getStorageKey(), second.getStorageKey());
        }

        @Test
        @DisplayName("이 앨범에 없는 ID 는 건너뛰고 실제로 지운 ID 만 준다")
        void skipsMissingIds() {
            // given
            givenAlbum(1L, true, LocalDateTime.now().plusDays(1));
            Photo photo = PhotoFixture.uploaded(12L, ALBUM_ID, 1L, HASH_A);
            given(photoRepository.findAllByAlbumIdAndIds(ALBUM_ID, List.of(12L, 999L)))
                    .willReturn(List.of(photo));

            // when
            PhotoDeleteResult result =
                    photoCommandService.deletePhotos(1L, ALBUM_ID, new PhotoDeleteCommand(List.of(12L, 999L)));

            // then
            assertThat(result.deletedPhotoIds()).containsExactly(12L);
        }

        @Test
        @DisplayName("중복 ID 는 한 번만 조회한다")
        void deduplicatesIds() {
            // given
            givenAlbum(1L, true, LocalDateTime.now().plusDays(1));
            given(photoRepository.findAllByAlbumIdAndIds(ALBUM_ID, List.of(12L)))
                    .willReturn(List.of(PhotoFixture.uploaded(12L, ALBUM_ID, 1L, HASH_A)));

            // when
            PhotoDeleteResult result =
                    photoCommandService.deletePhotos(1L, ALBUM_ID, new PhotoDeleteCommand(List.of(12L, 12L)));

            // then
            assertThat(result.deletedPhotoIds()).containsExactly(12L);
        }

        @Test
        @DisplayName("앨범장은 남이 올린 사진도 지운다")
        void ownerDeletesOthersPhoto() {
            // given
            givenAlbum(OWNER_ID, true, LocalDateTime.now().plusDays(1));
            Photo photo = PhotoFixture.uploaded(12L, ALBUM_ID, 1L, HASH_A);
            given(photoRepository.findAllByAlbumIdAndIds(ALBUM_ID, List.of(12L)))
                    .willReturn(List.of(photo));

            // when
            PhotoDeleteResult result =
                    photoCommandService.deletePhotos(OWNER_ID, ALBUM_ID, new PhotoDeleteCommand(List.of(12L)));

            // then
            assertThat(result.deletedPhotoIds()).containsExactly(12L);
            then(photoRepository).should().deleteAll(List.of(photo));
        }

        @Test
        @DisplayName("남의 사진이 섞이면 PhotoNotDeletableException 이고 아무것도 지우지 않는다")
        void rejectsAllWhenAnyIsNotDeletable() {
            // given
            givenAlbum(1L, true, LocalDateTime.now().plusDays(1));
            given(photoRepository.findAllByAlbumIdAndIds(ALBUM_ID, List.of(12L, 13L)))
                    .willReturn(List.of(
                            PhotoFixture.uploaded(12L, ALBUM_ID, 1L, HASH_A),
                            PhotoFixture.uploaded(13L, ALBUM_ID, 2L, HASH_B)));

            // when & then
            assertThatThrownBy(() ->
                            photoCommandService.deletePhotos(1L, ALBUM_ID, new PhotoDeleteCommand(List.of(12L, 13L))))
                    .isInstanceOf(PhotoNotDeletableException.class);
            then(photoRepository).should(never()).deleteAll(anyList());
            then(photoStorageDeletionRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("참여자가 아니면 PhotoDeleteNotAlbumMemberException")
        void rejectsNonMember() {
            // given
            givenAlbum(3L, false, LocalDateTime.now().plusDays(1));
            given(photoRepository.findAllByAlbumIdAndIds(ALBUM_ID, List.of(12L)))
                    .willReturn(List.of());

            // when & then
            assertThatThrownBy(
                            () -> photoCommandService.deletePhotos(3L, ALBUM_ID, new PhotoDeleteCommand(List.of(12L))))
                    .isInstanceOf(PhotoDeleteNotAlbumMemberException.class);
            then(photoRepository).should(never()).deleteAll(anyList());
        }

        @Test
        @DisplayName("만료된 앨범이면 PhotoDeleteAlbumExpiredException")
        void rejectsExpiredAlbum() {
            // given
            givenAlbum(1L, true, LocalDateTime.now().minusDays(1));
            given(photoRepository.findAllByAlbumIdAndIds(ALBUM_ID, List.of(12L)))
                    .willReturn(List.of(PhotoFixture.uploaded(12L, ALBUM_ID, 1L, HASH_A)));

            // when & then
            assertThatThrownBy(
                            () -> photoCommandService.deletePhotos(1L, ALBUM_ID, new PhotoDeleteCommand(List.of(12L))))
                    .isInstanceOf(PhotoDeleteAlbumExpiredException.class);
            then(photoRepository).should(never()).deleteAll(anyList());
        }

        private void givenAlbum(Long userId, boolean member, LocalDateTime expiresAt) {
            given(albumQueryApi.getInfo(ALBUM_ID)).willReturn(new AlbumInfo(ALBUM_ID, OWNER_ID, expiresAt));
            given(albumQueryApi.isMember(ALBUM_ID, userId)).willReturn(member);
        }
    }

    private void givenMemberOfOpenAlbum(Long userId) {
        given(albumQueryApi.getInfo(ALBUM_ID))
                .willReturn(new AlbumInfo(ALBUM_ID, 99L, LocalDateTime.now().plusDays(1)));
        given(albumQueryApi.isMember(ALBUM_ID, userId)).willReturn(true);
    }

    // IDENTITY 전략처럼 저장하면 id 가 채워지게 한다 (100 부터 순서대로).
    private void givenSaveAllAssignsIds() {
        given(photoRepository.saveAll(anyList())).willAnswer(invocation -> {
            List<Photo> photos = invocation.getArgument(0);
            long id = 100L;
            for (Photo photo : photos) {
                ReflectionTestUtils.setField(photo, "id", id++);
            }
            return photos;
        });
    }

    private void givenUploadUrls() throws Exception {
        given(photoStorage.uploadUrlExpiry()).willReturn(Duration.ofMinutes(15));
        for (String hash : List.of(HASH_A, HASH_B)) {
            lenient()
                    .when(photoStorage.createUploadUrl("albums/10/originals/" + hash, "image/jpeg"))
                    .thenReturn(URI.create(uploadUrl(hash)).toURL());
        }
    }

    private static String uploadUrl(String hash) {
        return "http://storage/upload/" + hash.charAt(0);
    }

    private static UploadFile file(String hash) {
        return new UploadFile(hash, PhotoFixture.BYTE_SIZE, "image/jpeg");
    }

    private static PhotoUploadCommand command(UploadFile... files) {
        return new PhotoUploadCommand(List.of(files));
    }
}
