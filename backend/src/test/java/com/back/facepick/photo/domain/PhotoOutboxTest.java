package com.back.facepick.photo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhotoOutboxTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Test
    @DisplayName("발행 전 상태로 만든다")
    void createsUnpublished() {
        // when
        PhotoOutbox outbox = PhotoOutbox.create("photo.uploaded", "10", "{}", NOW);

        // then
        assertThat(outbox.getTopic()).isEqualTo("photo.uploaded");
        assertThat(outbox.getMessageKey()).isEqualTo("10");
        assertThat(outbox.getCreatedAt()).isEqualTo(NOW);
        assertThat(outbox.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("발행 시각을 기록한다")
    void marksPublished() {
        // given
        PhotoOutbox outbox = PhotoOutbox.create("photo.uploaded", "10", "{}", NOW);

        // when
        outbox.markPublished(NOW.plusSeconds(1));

        // then
        assertThat(outbox.getPublishedAt()).isEqualTo(NOW.plusSeconds(1));
    }
}
