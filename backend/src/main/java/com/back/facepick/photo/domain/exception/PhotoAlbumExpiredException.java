package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoAlbumExpiredException extends BusinessException {
    public PhotoAlbumExpiredException() {
        super(PhotoErrorCode.PHOTO_ALBUM_EXPIRED);
    }
}
