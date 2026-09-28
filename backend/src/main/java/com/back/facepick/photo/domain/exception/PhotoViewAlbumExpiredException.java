package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoViewAlbumExpiredException extends BusinessException {
    public PhotoViewAlbumExpiredException() {
        super(PhotoErrorCode.PHOTO_VIEW_ALBUM_EXPIRED);
    }
}
