package com.back.facepick.photo.application.dto.result;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoStatus;

public record PhotoCompleteResult(Long photoId, PhotoStatus status) {
    public static PhotoCompleteResult from(Photo photo) {
        return new PhotoCompleteResult(photo.getId(), photo.getStatus());
    }
}
