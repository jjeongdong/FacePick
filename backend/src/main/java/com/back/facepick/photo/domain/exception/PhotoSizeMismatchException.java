package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoSizeMismatchException extends BusinessException {
    public PhotoSizeMismatchException() {
        super(PhotoErrorCode.PHOTO_SIZE_MISMATCH);
    }
}
