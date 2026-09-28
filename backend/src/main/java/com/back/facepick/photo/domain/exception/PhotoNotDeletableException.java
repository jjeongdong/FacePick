package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoNotDeletableException extends BusinessException {
    public PhotoNotDeletableException() {
        super(PhotoErrorCode.PHOTO_NOT_DELETABLE);
    }
}
