package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoTooLargeException extends BusinessException {
    public PhotoTooLargeException() {
        super(PhotoErrorCode.PHOTO_TOO_LARGE);
    }
}
