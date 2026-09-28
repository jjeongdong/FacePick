package com.back.facepick.album.domain.exception;

import com.back.facepick.album.domain.AlbumErrorCode;
import com.back.facepick.global.error.BusinessException;

public class AlbumInviteNotFoundException extends BusinessException {
    public AlbumInviteNotFoundException() {
        super(AlbumErrorCode.ALBUM_INVITE_NOT_FOUND);
    }
}
