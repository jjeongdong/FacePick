package com.back.facepick.photo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class AlbumStorageDeletionTest {

    @Test
    @DisplayName("스토리지 prefix 는 사진 저장 키와 같은 albums/{albumId}/ 이고 '/' 로 끝난다")
    void storagePrefix() {
        // given
        AlbumStorageDeletion deletion = newDeletion(7L);

        // when & then
        assertThat(deletion.storagePrefix()).isEqualTo("albums/7/");
    }

    @Test
    @DisplayName("지운 시각을 기록한다")
    void markDeleted() {
        // given
        AlbumStorageDeletion deletion = newDeletion(7L);
        LocalDateTime now = LocalDateTime.of(2026, 9, 1, 12, 0);

        // when
        deletion.markDeleted(now);

        // then
        assertThat(deletion.getDeletedAt()).isEqualTo(now);
    }

    // 행은 네이티브 INSERT 로만 만들어 생성 메서드가 없다. 단위 테스트용으로 protected 생성자·리플렉션으로 채운다.
    private static AlbumStorageDeletion newDeletion(Long albumId) {
        AlbumStorageDeletion deletion = BeanUtils.instantiateClass(AlbumStorageDeletion.class);
        ReflectionTestUtils.setField(deletion, "albumId", albumId);
        return deletion;
    }
}
