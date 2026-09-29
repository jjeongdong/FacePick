package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoDeleteAlbumExpiredException extends BusinessException {
    public PhotoDeleteAlbumExpiredException() {
        super(PhotoErrorCode.PHOTO_DELETE_ALBUM_EXPIRED);
    }
}
