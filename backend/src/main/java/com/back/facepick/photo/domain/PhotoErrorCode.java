package com.back.facepick.photo.domain;

import com.back.facepick.global.error.ErrorCode;
import com.back.facepick.global.error.ErrorType;

public enum PhotoErrorCode implements ErrorCode {
    PHOTO_NOT_ALBUM_MEMBER(ErrorType.FORBIDDEN, "앨범 참여자만 사진을 올릴 수 있습니다."),
    PHOTO_ALBUM_EXPIRED(ErrorType.INVALID, "만료된 앨범에는 사진을 올릴 수 없습니다."),
    PHOTO_UNSUPPORTED_TYPE(ErrorType.INVALID, "지원하지 않는 파일 형식입니다."),
    PHOTO_TOO_LARGE(ErrorType.INVALID, "파일은 100MB 이하여야 합니다."),
    PHOTO_NOT_FOUND(ErrorType.NOT_FOUND, "존재하지 않는 사진입니다."),
    PHOTO_NOT_UPLOADER(ErrorType.FORBIDDEN, "업로드한 사람만 완료할 수 있습니다."),
    PHOTO_FILE_MISSING(ErrorType.CONFLICT, "스토리지에 파일이 아직 올라가지 않았습니다."),
    PHOTO_VIEW_NOT_ALBUM_MEMBER(ErrorType.FORBIDDEN, "앨범 참여자만 사진을 볼 수 있습니다."),
    PHOTO_VIEW_ALBUM_EXPIRED(ErrorType.INVALID, "만료된 앨범의 사진은 볼 수 없습니다."),
    PHOTO_DELETE_NOT_ALBUM_MEMBER(ErrorType.FORBIDDEN, "앨범 참여자만 사진을 삭제할 수 있습니다."),
    PHOTO_DELETE_ALBUM_EXPIRED(ErrorType.INVALID, "만료된 앨범의 사진은 삭제할 수 없습니다."),
    PHOTO_NOT_DELETABLE(ErrorType.FORBIDDEN, "직접 올린 사진만 삭제할 수 있습니다. 앨범장은 모든 사진을 삭제할 수 있습니다."),
    PHOTO_SIZE_MISMATCH(ErrorType.CONFLICT, "올라간 파일 크기가 요청한 크기와 다릅니다.");

    private final ErrorType type;
    private final String message;

    PhotoErrorCode(ErrorType type, String message) {
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
