package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoNotFoundException extends BusinessException {
    public PhotoNotFoundException() {
        super(PhotoErrorCode.PHOTO_NOT_FOUND);
    }
}
