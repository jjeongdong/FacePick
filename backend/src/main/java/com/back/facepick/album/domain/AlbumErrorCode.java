package com.back.facepick.album.domain;

import com.back.facepick.global.error.ErrorCode;
import com.back.facepick.global.error.ErrorType;

public enum AlbumErrorCode implements ErrorCode {
    ALBUM_NOT_FOUND(ErrorType.NOT_FOUND, "존재하지 않는 앨범입니다."),
    ALBUM_INVALID_TITLE(ErrorType.INVALID, "앨범 제목은 1~50자여야 합니다."),
    ALBUM_NOT_MEMBER(ErrorType.FORBIDDEN, "앨범 참여자만 볼 수 있습니다.");

    private final ErrorType type;
    private final String message;

    AlbumErrorCode(ErrorType type, String message) {
        this.type = type;
        this.message = message;
    }

    @Override
    public ErrorType type() {
        return type;
    }

    @Override
    public String message() {
        return message;
    }
}
