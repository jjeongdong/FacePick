package com.back.facepick.photo.application.dto.result;

import com.back.facepick.photo.domain.Photo;
import java.time.LocalDateTime;
import java.util.List;

// 건너뛴 ID(없음·업로드 전·다른 앨범)는 빠지고 받을 수 있는 사진만, photoId 오름차순.
public record PhotoDownloadResult(List<Item> photos, LocalDateTime urlExpiresAt) {

    // 원본 URL 로 받으면 fileName 으로 저장된다.
    public record Item(Long photoId, String fileName, String originalUrl) {
        public static Item of(Photo photo, String originalUrl) {
            return new Item(photo.getId(), photo.downloadFileName(), originalUrl);
        }
    }
}
