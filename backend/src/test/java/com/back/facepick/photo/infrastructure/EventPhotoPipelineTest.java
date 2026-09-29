package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoOutbox;
import com.back.facepick.photo.domain.PhotoOutboxRepository;
import com.back.facepick.photo.fixture.PhotoFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EventPhotoPipelineTest {

    @Mock
    private PhotoOutboxRepository photoOutboxRepository;

    @Spy
    private JsonMapper jsonMapper = JsonMapper.builder().build();

    @InjectMocks
    private EventPhotoPipeline pipeline;

    @Test
    @DisplayName("새로 완료된 사진의 photo.uploaded 를 앨범 ID 키로 outbox 에 기록한다")
    void writesPhotoUploadedToOutbox() {
        // given
        Photo photo = PhotoFixture.uploaded(12L, 10L, 1L, PhotoFixture.HASH);

        // when
        pipeline.onCompleted(photo, PhotoFixture.NOW);

        // then
        ArgumentCaptor<PhotoOutbox> captor = ArgumentCaptor.forClass(PhotoOutbox.class);
        then(photoOutboxRepository).should().save(captor.capture());
        PhotoOutbox outbox = captor.getValue();
        assertThat(outbox.getTopic()).isEqualTo("photo.uploaded");
        assertThat(outbox.getMessageKey()).isEqualTo("10");
        JsonNode payload = jsonMapper.readTree(outbox.getPayload());
        assertThat(payload.get("photoId").asLong()).isEqualTo(12L);
        assertThat(payload.get("albumId").asLong()).isEqualTo(10L);
        assertThat(payload.get("storageKey").asString()).isEqualTo(photo.getStorageKey());
        assertThat(payload.get("contentType").asString()).isEqualTo("image/jpeg");
        assertThat(payload.get("byteSize").asLong()).isEqualTo(PhotoFixture.BYTE_SIZE);
    }

    @Test
    @DisplayName("커밋 뒤에는 아무것도 하지 않는다")
    void doesNothingAfterCommit() {
        // when
        pipeline.afterCommit(12L);

        // then
        then(photoOutboxRepository).shouldHaveNoInteractions();
    }
}
