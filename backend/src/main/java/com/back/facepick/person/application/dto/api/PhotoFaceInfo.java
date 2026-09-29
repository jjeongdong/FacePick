package com.back.facepick.person.application.dto.api;

import com.back.facepick.person.domain.PhotoFaceAnalysis;

// personId 는 얼굴이 없으면 null.
public record PhotoFaceInfo(int faceCount, Long personId) {

    public static PhotoFaceInfo from(PhotoFaceAnalysis analysis) {
        return new PhotoFaceInfo(analysis.faceCount(), analysis.personId());
    }
}
