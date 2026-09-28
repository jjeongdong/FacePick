package com.back.facepick.photo.domain;

import com.back.facepick.global.persistence.BaseTimeEntity;
import com.back.facepick.photo.domain.exception.PhotoFileMissingException;
import com.back.facepick.photo.domain.exception.PhotoNotUploaderException;
import com.back.facepick.photo.domain.exception.PhotoSizeMismatchException;
import com.back.facepick.photo.domain.exception.PhotoTooLargeException;
import com.back.facepick.photo.domain.exception.PhotoUnsupportedTypeException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "photos")
public class Photo extends BaseTimeEntity {
    // 단일 PUT 업로드라 한 파일이 너무 크면 끊겼을 때 처음부터 다시 올려야 한다. DNG 를 고려한 상한.
    private static final long MAX_BYTE_SIZE = 100L * 1024 * 1024;
    // PRD F5: 원본 그대로 보관하는 형식. 라이브 포토 영상(MOV)은 아직 다루지 않는다.
    private static final Set<String> SUPPORTED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/heic", "image/heif", "image/x-adobe-dng");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_id")
    private Long id;

    @Column(name = "album_id", nullable = false)
    private Long albumId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "byte_size", nullable = false)
    private Long byteSize;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhotoStatus status;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    private Photo(
            Long albumId, Long uploaderId, String contentHash, Long byteSize, String contentType, String storageKey) {
        this.albumId = albumId;
        this.uploaderId = uploaderId;
        this.contentHash = contentHash;
        this.byteSize = byteSize;
        this.contentType = contentType;
        this.storageKey = storageKey;
        this.status = PhotoStatus.PENDING;
    }

    public static Photo create(Long albumId, Long uploaderId, String contentHash, Long byteSize, String contentType) {
        validateFile(contentType, byteSize);
        // 해시는 앨범 안에서 유일하므로 저장 전에 키를 정할 수 있다.
        String storageKey = "albums/" + albumId + "/originals/" + contentHash;
        return new Photo(albumId, uploaderId, contentHash, byteSize, contentType, storageKey);
    }

    // 이미 등록된 해시를 다시 요청할 때도 선언 값을 검사해야 해서 create 와 따로 둔다.
    public static void validateFile(String contentType, Long byteSize) {
        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new PhotoUnsupportedTypeException();
        }
        if (byteSize > MAX_BYTE_SIZE) {
            throw new PhotoTooLargeException();
        }
    }

    public boolean isUploaded() {
        return status == PhotoStatus.UPLOADED;
    }

    // 먼저 시작한 사람이 업로드를 그만둬도 같은 파일을 다시 요청한 사람이 끝낼 수 있게 한다.
    public void reassignUploader(Long userId) {
        this.uploaderId = userId;
    }

    /**
     * @param storedByteSize 스토리지에 실제로 올라간 크기. 파일이 없으면 null
     * @return 이번 호출로 UPLOADED 가 됐으면 true, 이미 UPLOADED 였으면 false
     */
    public boolean complete(Long userId, Long storedByteSize, LocalDateTime now) {
        // 업로더가 바뀐 뒤 원래 업로더가 재시도해도 403 이 되지 않게, 이미 끝난 사진은 누구에게나 멱등하게 답한다.
        if (isUploaded()) {
            return false;
        }
        if (!uploaderId.equals(userId)) {
            throw new PhotoNotUploaderException();
        }
        if (storedByteSize == null) {
            throw new PhotoFileMissingException();
        }
        if (!byteSize.equals(storedByteSize)) {
            throw new PhotoSizeMismatchException();
        }
        this.status = PhotoStatus.UPLOADED;
        this.uploadedAt = now;
        return true;
    }
}
