package com.back.facepick.photo.application.dto.result;

import com.back.facepick.photo.domain.PhotoSelfieStatus;

// 없는 값은 null 이다: NONE 이면 전부, 썸네일 전이면 thumbnailUrl, READY 가 아니면 personId.
public record PhotoSelfieResult(PhotoSelfieStatus status, Long photoId, String thumbnailUrl, Long personId) {

    public static PhotoSelfieResult none() {
        return new PhotoSelfieResult(PhotoSelfieStatus.NONE, null, null, null);
    }
}
