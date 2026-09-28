package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoViewNotAlbumMemberException extends BusinessException {
    public PhotoViewNotAlbumMemberException() {
        super(PhotoErrorCode.PHOTO_VIEW_NOT_ALBUM_MEMBER);
    }
}
