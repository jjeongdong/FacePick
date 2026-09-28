package com.back.facepick.photo.application.dto.result;

import com.back.facepick.photo.domain.Photo;
import java.time.LocalDateTime;

// 워커 처리 전 사진은 previewUrl·width·height·takenAt 이 null 이고, 원본은 언제나 받을 수 있다.
public record PhotoDetailResult(
        Long photoId,
        Long albumId,
        String contentType,
        Long byteSize,
        Integer width,
        Integer height,
        LocalDateTime takenAt,
        LocalDateTime uploadedAt,
        String previewUrl,
        String originalUrl,
        LocalDateTime urlExpiresAt) {
    public static PhotoDetailResult of(Photo photo, String previewUrl, String originalUrl, LocalDateTime urlExpiresAt) {
        return new PhotoDetailResult(
                photo.getId(),
                photo.getAlbumId(),
                photo.getContentType(),
                photo.getByteSize(),
                photo.getWidth(),
                photo.getHeight(),
                photo.getTakenAt(),
                photo.getUploadedAt(),
                previewUrl,
                originalUrl,
                urlExpiresAt);
    }
}
