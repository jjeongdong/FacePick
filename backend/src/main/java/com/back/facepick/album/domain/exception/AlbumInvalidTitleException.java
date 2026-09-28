package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumInvalidTitleException extends BusinessException {
    public AlbumInvalidTitleException() {
        super(AlbumErrorCode.ALBUM_INVALID_TITLE);
    }
}
