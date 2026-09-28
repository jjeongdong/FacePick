package com.back.facepick.photo.domain;

import com.back.facepick.photo.domain.exception.PhotoViewAlbumExpiredException;
import com.back.facepick.photo.domain.exception.PhotoViewNotAlbumMemberException;
import java.time.LocalDateTime;

// 업로드 규칙과 같지만 에러 메시지가 달라 따로 둔다. 앨범 값은 album BC 에서 받아 인자로 넘긴다.
public final class PhotoViewPolicy {

    private PhotoViewPolicy() {}

    public static void validate(boolean albumMember, LocalDateTime albumExpiresAt, LocalDateTime now) {
        if (!albumMember) {
            throw new PhotoViewNotAlbumMemberException();
        }
        // PRD 상 만료된 앨범은 삭제된 앨범이다. 자동 삭제가 생기기 전에도 같은 동작을 하게 막는다.
        if (!now.isBefore(albumExpiresAt)) {
            throw new PhotoViewAlbumExpiredException();
        }
    }
}
