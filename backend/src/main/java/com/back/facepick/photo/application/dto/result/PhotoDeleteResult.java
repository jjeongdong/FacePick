package com.back.facepick.photo.application.dto.result;

import com.back.facepick.photo.domain.Photo;
import java.util.List;

// 건너뛴 ID 는 빠지고 실제로 지운 사진만, photoId 오름차순.
public record PhotoDeleteResult(List<Long> deletedPhotoIds) {
    public static PhotoDeleteResult from(List<Photo> photos) {
        return new PhotoDeleteResult(photos.stream().map(Photo::getId).sorted().toList());
    }
}
