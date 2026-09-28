package com.back.facepick.photo.domain;

import java.time.LocalDateTime;

// 목록 정렬 키(uploaded_at DESC, photo_id DESC). 다음 페이지는 이 값보다 뒤에 오는 사진부터다.
public record PhotoCursor(LocalDateTime uploadedAt, Long photoId) {
    public static PhotoCursor from(Photo photo) {
        return new PhotoCursor(photo.getUploadedAt(), photo.getId());
    }
}
