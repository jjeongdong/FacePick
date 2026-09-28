package com.back.facepick.photo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhotoStorageDeletionTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("원본 키로 원본·썸네일·미리보기 키를 만든다 (썸네일 워커와 같은 규칙)")
    void derivesStorageKeys() {
        // given
        PhotoStorageDeletion deletion = PhotoStorageDeletion.create("albums/10/originals/" + HASH, NOW);

        // when & then
        assertThat(deletion.storageKeys())
                .containsExactly(
                        "albums/10/originals/" + HASH,
                        "albums/10/thumbnails/" + HASH + ".jpg",
                        "albums/10/previews/" + HASH + ".jpg");
    }

    @Test
    @DisplayName("지운 시각을 기록한다")
    void marksDeleted() {
        // given
        PhotoStorageDeletion deletion = PhotoStorageDeletion.create("albums/10/originals/" + HASH, NOW);

        // when
        deletion.markDeleted(NOW.plusMinutes(20));

        // then
        assertThat(deletion.getCreatedAt()).isEqualTo(NOW);
        assertThat(deletion.getDeletedAt()).isEqualTo(NOW.plusMinutes(20));
    }
}
