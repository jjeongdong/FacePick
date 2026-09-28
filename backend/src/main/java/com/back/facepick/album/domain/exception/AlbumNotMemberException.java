package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumNotMemberException extends BusinessException {
    public AlbumNotMemberException() {
        super(AlbumErrorCode.ALBUM_NOT_MEMBER);
    }
}
