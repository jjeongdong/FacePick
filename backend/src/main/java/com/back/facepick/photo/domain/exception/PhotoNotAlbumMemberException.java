package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoNotAlbumMemberException extends BusinessException {
    public PhotoNotAlbumMemberException() {
        super(PhotoErrorCode.PHOTO_NOT_ALBUM_MEMBER);
    }
}
