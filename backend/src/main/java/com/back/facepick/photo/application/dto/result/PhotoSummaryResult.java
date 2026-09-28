package com.back.facepick.photo.application.dto.result;

import com.back.facepick.photo.domain.Photo;
import java.time.LocalDateTime;

// 워커 처리 전 사진은 thumbnailUrl·width·height·takenAt 이 null 이다 (클라이언트는 빈 칸을 그린다).
public record PhotoSummaryResult(
        Long photoId,
        String thumbnailUrl,
        Integer width,
        Integer height,
        LocalDateTime takenAt,
        LocalDateTime uploadedAt,
        LocalDateTime urlExpiresAt) {
    public static PhotoSummaryResult of(Photo photo, String thumbnailUrl, LocalDateTime urlExpiresAt) {
        return new PhotoSummaryResult(
                photo.getId(),
                thumbnailUrl,
                photo.getWidth(),
                photo.getHeight(),
                photo.getTakenAt(),
                photo.getUploadedAt(),
                urlExpiresAt);
    }
}
