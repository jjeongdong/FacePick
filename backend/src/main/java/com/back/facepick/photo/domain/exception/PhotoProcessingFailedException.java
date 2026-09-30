package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoProcessingFailedException extends BusinessException {
    public PhotoProcessingFailedException() {
        super(PhotoErrorCode.PHOTO_PROCESSING_FAILED);
    }
}
