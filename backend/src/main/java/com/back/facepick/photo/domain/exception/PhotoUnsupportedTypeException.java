package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoUnsupportedTypeException extends BusinessException {
    public PhotoUnsupportedTypeException() {
        super(PhotoErrorCode.PHOTO_UNSUPPORTED_TYPE);
    }
}
