package com.back.facepick.photo.domain;

import com.back.facepick.photo.domain.exception.PhotoDeleteAlbumExpiredException;
import com.back.facepick.photo.domain.exception.PhotoDeleteNotAlbumMemberException;
import com.back.facepick.photo.domain.exception.PhotoNotDeletableException;
import java.time.LocalDateTime;
import java.util.List;

// 앨범 값은 album BC 에서 받아 인자로 넘긴다. 한 장이라도 권한이 없으면 전체를 거절해 부분 삭제가 생기지 않게 한다.
public final class PhotoDeletePolicy {

    private PhotoDeletePolicy() {}

    public static void validate(
            boolean albumMember,
            LocalDateTime albumExpiresAt,
            LocalDateTime now,
            Long userId,
            Long albumOwnerId,
            List<Photo> photos) {
        if (!albumMember) {
            throw new PhotoDeleteNotAlbumMemberException();
        }
        if (!now.isBefore(albumExpiresAt)) {
            throw new PhotoDeleteAlbumExpiredException();
        }
        for (Photo photo : photos) {
            if (!photo.canBeDeletedBy(userId, albumOwnerId)) {
                throw new PhotoNotDeletableException();
            }
        }
    }
}
