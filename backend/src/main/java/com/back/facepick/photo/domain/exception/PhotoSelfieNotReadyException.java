package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoSelfieNotReadyException extends BusinessException {
    public PhotoSelfieNotReadyException() {
        super(PhotoErrorCode.PHOTO_SELFIE_NOT_READY);
    }
}
