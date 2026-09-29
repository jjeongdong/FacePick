package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoDeleteNotAlbumMemberException extends BusinessException {
    public PhotoDeleteNotAlbumMemberException() {
        super(PhotoErrorCode.PHOTO_DELETE_NOT_ALBUM_MEMBER);
    }
}
