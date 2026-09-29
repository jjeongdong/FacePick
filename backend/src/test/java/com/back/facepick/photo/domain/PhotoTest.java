package com.back.facepick.photo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.photo.domain.exception.PhotoFileMissingException;
import com.back.facepick.photo.domain.exception.PhotoNotUploaderException;
import com.back.facepick.photo.domain.exception.PhotoSizeMismatchException;
import com.back.facepick.photo.domain.exception.PhotoTooLargeException;
import com.back.facepick.photo.domain.exception.PhotoUnsupportedTypeException;
import com.back.facepick.photo.fixture.PhotoFixture;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class PhotoTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final String HASH = "a".repeat(64);
    private static final long MAX_BYTE_SIZE = 100L * 1024 * 1024;

    @Nested
    @DisplayName("사진 생성")
    class Create {

        @Test
        @DisplayName("PENDING 상태로 만들고 앨범·해시로 저장 키를 정한다")
        void createsPendingWithStorageKey() {
            // when
            Photo photo = Photo.create(10L, 1L, HASH, 1000L, "image/heic");

            // then
            assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING);
            assertThat(photo.getStorageKey()).isEqualTo("albums/10/originals/" + HASH);
            assertThat(photo.getUploadedAt()).isNull();
        }

        @Test
        @DisplayName("100MB 까지는 허용한다")
        void allowsMaxSize() {
            // when
            Photo photo = Photo.create(10L, 1L, HASH, MAX_BYTE_SIZE, "image/x-adobe-dng");

            // then
            assertThat(photo.getByteSize()).isEqualTo(MAX_BYTE_SIZE);
        }

        @Test
        @DisplayName("100MB 를 넘으면 PhotoTooLargeException")
        void throwsWhenTooLarge() {
            // when & then
            assertThatThrownBy(() -> Photo.create(10L, 1L, HASH, MAX_BYTE_SIZE + 1, "image/jpeg"))
                    .isInstanceOf(PhotoTooLargeException.class);
        }

        @Test
        @DisplayName("허용하지 않는 형식이면 PhotoUnsupportedTypeException")
        void throwsWhenUnsupportedType() {
            // when & then
            assertThatThrownBy(() -> Photo.create(10L, 1L, HASH, 1000L, "video/quicktime"))
                    .isInstanceOf(PhotoUnsupportedTypeException.class);
        }
    }

    @Test
    @DisplayName("업로더를 요청자로 바꾼다")
    void reassignsUploader() {
        // given
        Photo photo = Photo.create(10L, 1L, HASH, 1000L, "image/jpeg");

        // when
        photo.reassignUploader(2L);

        // then
        assertThat(photo.getUploaderId()).isEqualTo(2L);
    }

    @Nested
    @DisplayName("업로드 완료")
    class Complete {

        @Test
        @DisplayName("크기가 맞으면 UPLOADED 로 바꾸고 true")
        void completesWhenSizeMatches() {
            // given
            Photo photo = Photo.create(10L, 1L, HASH, 1000L, "image/jpeg");

            // when
            boolean completed = photo.complete(1L, 1000L, NOW);

            // then
            assertThat(completed).isTrue();
            assertThat(photo.isUploaded()).isTrue();
            assertThat(photo.getUploadedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("이미 UPLOADED 면 아무것도 바꾸지 않고 false")
        void returnsFalseWhenAlreadyUploaded() {
            // given
            Photo photo = Photo.create(10L, 1L, HASH, 1000L, "image/jpeg");
            photo.complete(1L, 1000L, NOW);

            // when
            boolean completed = photo.complete(1L, 1000L, NOW.plusMinutes(1));

            // then
            assertThat(completed).isFalse();
            assertThat(photo.getUploadedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("이미 UPLOADED 면 업로더가 아니어도 예외 없이 false (재시도 안전)")
        void returnsFalseForAnyoneWhenAlreadyUploaded() {
            // given
            Photo photo = Photo.create(10L, 1L, HASH, 1000L, "image/jpeg");
            photo.complete(1L, 1000L, NOW);

            // when
            boolean completed = photo.complete(2L, 1000L, NOW);

            // then
            assertThat(completed).isFalse();
        }

        @Test
        @DisplayName("PENDING 인데 업로더가 아니면 PhotoNotUploaderException")
        void throwsWhenNotUploader() {
            // given
            Photo photo = Photo.create(10L, 1L, HASH, 1000L, "image/jpeg");

            // when & then
            assertThatThrownBy(() -> photo.complete(2L, 1000L, NOW)).isInstanceOf(PhotoNotUploaderException.class);
        }

        @Test
        @DisplayName("스토리지에 파일이 없으면 PhotoFileMissingException")
        void throwsWhenFileMissing() {
            // given
            Photo photo = Photo.create(10L, 1L, HASH, 1000L, "image/jpeg");

            // when & then
            assertThatThrownBy(() -> photo.complete(1L, null, NOW)).isInstanceOf(PhotoFileMissingException.class);
        }

        @Test
        @DisplayName("올라간 크기가 다르면 PhotoSizeMismatchException 이고 PENDING 그대로")
        void throwsWhenSizeMismatch() {
            // given
            Photo photo = Photo.create(10L, 1L, HASH, 1000L, "image/jpeg");

            // when & then
            assertThatThrownBy(() -> photo.complete(1L, 999L, NOW)).isInstanceOf(PhotoSizeMismatchException.class);
            assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("처리 여부")
    class IsProcessed {

        @Test
        @DisplayName("워커가 처리하기 전이면 false")
        void falseBeforeWorker() {
            // given
            Photo photo = PhotoFixture.uploaded(12L, 10L, 1L, HASH);

            // when & then
            assertThat(photo.isProcessed()).isFalse();
        }

        @Test
        @DisplayName("워커가 처리 시각을 남겼으면 true")
        void trueAfterWorker() {
            // given
            Photo photo = PhotoFixture.processed(12L, 10L, 1L, HASH);

            // when & then
            assertThat(photo.isProcessed()).isTrue();
        }
    }

    @Nested
    @DisplayName("다운로드 파일명")
    class DownloadFileName {

        @ParameterizedTest
        @CsvSource({
            "image/jpeg, jpg",
            "image/png, png",
            "image/heic, heic",
            "image/heif, heif",
            "image/x-adobe-dng, dng"
        })
        @DisplayName("사진 ID 와 업로드 허용 형식별 확장자로 만든다")
        void usesIdAndExtension(String contentType, String extension) {
            // given
            Photo photo = Photo.create(10L, 1L, HASH, 1000L, contentType);
            ReflectionTestUtils.setField(photo, "id", 12L);

            // when & then
            assertThat(photo.downloadFileName()).isEqualTo("facepick-12." + extension);
        }
    }

    @Nested
    @DisplayName("삭제 권한")
    class CanBeDeletedBy {

        @Test
        @DisplayName("업로더는 지울 수 있다")
        void uploaderCanDelete() {
            // given
            Photo photo = PhotoFixture.uploaded(12L, 10L, 1L, HASH);

            // when & then
            assertThat(photo.canBeDeletedBy(1L, 99L)).isTrue();
        }

        @Test
        @DisplayName("앨범장은 남이 올린 사진도 지울 수 있다")
        void albumOwnerCanDelete() {
            // given
            Photo photo = PhotoFixture.uploaded(12L, 10L, 1L, HASH);

            // when & then
            assertThat(photo.canBeDeletedBy(99L, 99L)).isTrue();
        }

        @Test
        @DisplayName("업로더도 앨범장도 아니면 지울 수 없다")
        void othersCannotDelete() {
            // given
            Photo photo = PhotoFixture.uploaded(12L, 10L, 1L, HASH);

            // when & then
            assertThat(photo.canBeDeletedBy(2L, 99L)).isFalse();
        }

        @Test
        @DisplayName("이어올리기로 업로더가 바뀌면 원래 업로더는 지울 수 없다")
        void formerUploaderCannotDelete() {
            // given
            Photo photo = PhotoFixture.pending(12L, 10L, 1L);
            photo.reassignUploader(2L);

            // when & then
            assertThat(photo.canBeDeletedBy(1L, 99L)).isFalse();
            assertThat(photo.canBeDeletedBy(2L, 99L)).isTrue();
        }
    }
}
