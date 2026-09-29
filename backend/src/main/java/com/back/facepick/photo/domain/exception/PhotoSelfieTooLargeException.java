package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoSelfieTooLargeException extends BusinessException {
    public PhotoSelfieTooLargeException() {
        super(PhotoErrorCode.PHOTO_SELFIE_TOO_LARGE);
    }
}
