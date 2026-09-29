package com.back.facepick.photo.domain;

public enum PhotoSelfieStatus {
    NONE,
    UPLOADING,
    PROCESSING,
    NO_FACE,
    READY;

    // 얼굴 분석은 person BC 가 하므로 분석 여부와 인물은 인자로 받는다. 셀피가 없을 때(NONE)는 부르지 않는다.
    public static PhotoSelfieStatus of(boolean uploaded, boolean analyzed, Long personId) {
        if (!uploaded) {
            return UPLOADING;
        }
        if (!analyzed) {
            return PROCESSING;
        }
        return personId == null ? NO_FACE : READY;
    }
}
