package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumExpiryNoticeNotPendingException extends BusinessException {
    public AlbumExpiryNoticeNotPendingException() {
        super(AlbumErrorCode.ALBUM_EXPIRY_NOTICE_NOT_PENDING);
    }
}
