package com.back.facepick.photo.application.dto.result;

import com.back.facepick.photo.domain.Photo;
import java.time.LocalDateTime;

// ALREADY_UPLOADED 면 uploadUrl·urlExpiresAt 이 null 이다 (같은 셀피를 다시 요청한 경우).
public record PhotoSelfieUploadResult(
        Long photoId, Status status, String contentType, String uploadUrl, LocalDateTime urlExpiresAt) {

    public enum Status {
        NEW,
        RESUMED,
        ALREADY_UPLOADED
    }

    public static PhotoSelfieUploadResult of(Photo photo, Status status, String uploadUrl, LocalDateTime urlExpiresAt) {
        return new PhotoSelfieUploadResult(photo.getId(), status, photo.getContentType(), uploadUrl, urlExpiresAt);
    }

    public static PhotoSelfieUploadResult alreadyUploaded(Photo photo) {
        return new PhotoSelfieUploadResult(photo.getId(), Status.ALREADY_UPLOADED, photo.getContentType(), null, null);
    }
}
