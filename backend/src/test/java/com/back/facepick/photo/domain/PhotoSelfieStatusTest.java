package com.back.facepick.photo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhotoSelfieStatusTest {

    @Test
    @DisplayName("업로드 전이면 UPLOADING")
    void uploading() {
        assertThat(PhotoSelfieStatus.of(false, false, null)).isEqualTo(PhotoSelfieStatus.UPLOADING);
    }

    @Test
    @DisplayName("업로드됐지만 분석 전이면 PROCESSING")
    void processing() {
        assertThat(PhotoSelfieStatus.of(true, false, null)).isEqualTo(PhotoSelfieStatus.PROCESSING);
    }

    @Test
    @DisplayName("분석이 끝났는데 인물이 없으면 NO_FACE")
    void noFace() {
        assertThat(PhotoSelfieStatus.of(true, true, null)).isEqualTo(PhotoSelfieStatus.NO_FACE);
    }

    @Test
    @DisplayName("분석이 끝나고 인물이 있으면 READY")
    void ready() {
        assertThat(PhotoSelfieStatus.of(true, true, 7L)).isEqualTo(PhotoSelfieStatus.READY);
    }
}
