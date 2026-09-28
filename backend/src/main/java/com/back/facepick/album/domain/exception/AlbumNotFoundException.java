package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumNotFoundException extends BusinessException {
    public AlbumNotFoundException() {
        super(AlbumErrorCode.ALBUM_NOT_FOUND);
    }
}
