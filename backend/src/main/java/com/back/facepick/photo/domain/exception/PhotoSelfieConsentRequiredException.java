package com.back.facepick.photo.domain.exception;

import com.back.facepick.global.error.BusinessException;
import com.back.facepick.photo.domain.PhotoErrorCode;

public class PhotoSelfieConsentRequiredException extends BusinessException {
    public PhotoSelfieConsentRequiredException() {
        super(PhotoErrorCode.PHOTO_SELFIE_CONSENT_REQUIRED);
    }
}
