package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoFileMissingException extends BusinessException {
    public PhotoFileMissingException() {
        super(PhotoErrorCode.PHOTO_FILE_MISSING);
    }
}
