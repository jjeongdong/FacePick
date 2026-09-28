package com.back.facepick.photo.application.dto.result;

import com.back.facepick.photo.domain.Photo;
import java.time.LocalDateTime;
import java.util.List;

public record PhotoUploadResult(LocalDateTime expiresAt, List<FileResult> files) {

    public enum Status {
        UPLOAD_REQUIRED,
        ALREADY_UPLOADED
    }

    public record FileResult(String contentHash, Long photoId, Status status, String uploadUrl) {

        public static FileResult uploadRequired(Photo photo, String uploadUrl) {
            return new FileResult(photo.getContentHash(), photo.getId(), Status.UPLOAD_REQUIRED, uploadUrl);
        }

        public static FileResult alreadyUploaded(Photo photo) {
            return new FileResult(photo.getContentHash(), photo.getId(), Status.ALREADY_UPLOADED, null);
        }
    }
}
