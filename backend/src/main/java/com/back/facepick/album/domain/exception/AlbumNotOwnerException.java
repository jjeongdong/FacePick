package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumNotOwnerException extends BusinessException {
    public AlbumNotOwnerException() {
        super(AlbumErrorCode.ALBUM_NOT_OWNER);
    }
}
