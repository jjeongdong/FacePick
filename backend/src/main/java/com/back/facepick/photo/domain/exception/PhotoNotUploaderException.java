package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoNotUploaderException extends BusinessException {
    public PhotoNotUploaderException() {
        super(PhotoErrorCode.PHOTO_NOT_UPLOADER);
    }
}
