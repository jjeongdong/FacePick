package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoProcessingUnavailableException extends BusinessException {
    public PhotoProcessingUnavailableException() {
        super(PhotoErrorCode.PHOTO_PROCESSING_UNAVAILABLE);
    }
}
