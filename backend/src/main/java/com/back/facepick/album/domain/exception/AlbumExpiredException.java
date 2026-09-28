package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumExpiredException extends BusinessException {
    public AlbumExpiredException() {
        super(AlbumErrorCode.ALBUM_EXPIRED);
    }
}
