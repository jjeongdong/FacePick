package com.back.facepick.photo.domain;

import com.back.facepick.photo.domain.exception.PhotoAlbumExpiredException;
import com.back.facepick.photo.domain.exception.PhotoNotAlbumMemberException;
import java.time.LocalDateTime;

// 앨범은 album BC 가 가지므로, 업로드 규칙에 필요한 값만 인자로 받는다.
public final class PhotoUploadPolicy {

    private PhotoUploadPolicy() {}

    public static void validate(boolean albumMember, LocalDateTime albumExpiresAt, LocalDateTime now) {
        if (!albumMember) {
            throw new PhotoNotAlbumMemberException();
        }
        if (!now.isBefore(albumExpiresAt)) {
            throw new PhotoAlbumExpiredException();
        }
    }
}
