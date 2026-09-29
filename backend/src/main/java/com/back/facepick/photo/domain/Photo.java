package com.back.facepick.photo.domain;

import com.back.facepick.global.persistence.BaseTimeEntity;
import com.back.facepick.photo.domain.exception.PhotoFileMissingException;
import com.back.facepick.photo.domain.exception.PhotoNotUploaderException;
import com.back.facepick.photo.domain.exception.PhotoSelfieConsentRequiredException;
import com.back.facepick.photo.domain.exception.PhotoSelfieTooLargeException;
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
import java.util.Map;
import java.util.Objects;
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
    // PRD F5: 원본 그대로 보관하는 형식과 다운로드 파일 확장자. 라이브 포토 영상(MOV)은 아직 다루지 않는다.
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/heic", "heic",
            "image/heif", "heif",
            "image/x-adobe-dng", "dng");
    private static final String DOWNLOAD_FILE_PREFIX = "facepick-";
    // 셀피는 폰 사진이라 이 정도면 충분하고, 워커가 처리하지 못하는 DNG 는 받지 않는다 (받으면 분석 중으로 영원히 남는다).
    private static final long MAX_SELFIE_BYTE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> SELFIE_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/heic", "image/heif");

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhotoPurpose purpose;

    // 아래는 썸네일 워커(Python)만 쓰는 컬럼이다. JPA 는 UPDATE 때 모든 컬럼을 쓰므로,
    // 백엔드가 사진을 고칠 때 워커가 쓴 값을 옛 값으로 덮지 않게 읽기 전용으로 둔다.
    @Column(name = "thumbnail_key", insertable = false, updatable = false)
    private String thumbnailKey;

    @Column(name = "preview_key", insertable = false, updatable = false)
    private String previewKey;

    @Column(insertable = false, updatable = false)
    private Integer width;

    @Column(insertable = false, updatable = false)
    private Integer height;

    @Column(name = "taken_at", insertable = false, updatable = false)
    private LocalDateTime takenAt;

    @Column(name = "processed_at", insertable = false, updatable = false)
    private LocalDateTime processedAt;

    private Photo(
            Long albumId,
            Long uploaderId,
            String contentHash,
            Long byteSize,
            String contentType,
            String storageKey,
            PhotoPurpose purpose) {
        this.albumId = albumId;
        this.uploaderId = uploaderId;
        this.contentHash = contentHash;
        this.byteSize = byteSize;
        this.contentType = contentType;
        this.storageKey = storageKey;
        this.status = PhotoStatus.PENDING;
        this.purpose = purpose;
    }

    public static Photo create(Long albumId, Long uploaderId, String contentHash, Long byteSize, String contentType) {
        validateFile(contentType, byteSize);
        return new Photo(
                albumId,
                uploaderId,
                contentHash,
                byteSize,
                contentType,
                originalKey(albumId, contentHash),
                PhotoPurpose.ALBUM);
    }

    public static Photo createSelfie(
            Long albumId,
            Long uploaderId,
            String contentHash,
            Long byteSize,
            String contentType,
            boolean faceAnalysisConsent) {
        validateSelfieFile(contentType, byteSize, faceAnalysisConsent);
        return new Photo(
                albumId,
                uploaderId,
                contentHash,
                byteSize,
                contentType,
                originalKey(albumId, contentHash),
                PhotoPurpose.SELFIE);
    }

    // 같은 셀피를 다시 요청할 때도 검사해야 해서 createSelfie 와 따로 둔다.
    public static void validateSelfieFile(String contentType, Long byteSize, boolean faceAnalysisConsent) {
        if (!faceAnalysisConsent) {
            throw new PhotoSelfieConsentRequiredException();
        }
        if (!SELFIE_CONTENT_TYPES.contains(contentType)) {
            throw new PhotoUnsupportedTypeException();
        }
        if (byteSize > MAX_SELFIE_BYTE_SIZE) {
            throw new PhotoSelfieTooLargeException();
        }
    }

    // 해시는 앨범 안에서 파일마다 같으므로 저장 전에 키를 정할 수 있다. 셀피가 앨범 사진과 같은 파일이면 키도 같다.
    private static String originalKey(Long albumId, String contentHash) {
        return "albums/" + albumId + "/originals/" + contentHash;
    }

    // 이미 등록된 해시를 다시 요청할 때도 선언 값을 검사해야 해서 create 와 따로 둔다.
    public static void validateFile(String contentType, Long byteSize) {
        if (!EXTENSIONS_BY_CONTENT_TYPE.containsKey(contentType)) {
            throw new PhotoUnsupportedTypeException();
        }
        if (byteSize > MAX_BYTE_SIZE) {
            throw new PhotoTooLargeException();
        }
    }

    public boolean hasFile(String contentHash) {
        return Objects.equals(this.contentHash, contentHash);
    }

    public boolean isUploaded() {
        return status == PhotoStatus.UPLOADED;
    }

    // 워커 처리 전(또는 DLQ 로 간) 사진은 썸네일·미리보기가 없다.
    public boolean isProcessed() {
        return processedAt != null;
    }

    // 저장 키에 확장자가 없고 원래 파일명은 받지 않아, 사진 ID 와 형식으로 이름을 만든다.
    public String downloadFileName() {
        return DOWNLOAD_FILE_PREFIX + id + "." + EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
    }

    // 이어올리기로 업로더가 바뀌면 바뀐 사람이 기준이다. 앨범장은 앨범의 모든 사진을 지울 수 있다 (PRD F1).
    public boolean canBeDeletedBy(Long userId, Long albumOwnerId) {
        return Objects.equals(uploaderId, userId) || Objects.equals(albumOwnerId, userId);
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
