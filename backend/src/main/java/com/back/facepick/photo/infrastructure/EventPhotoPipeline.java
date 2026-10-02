package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoOutbox;
import com.back.facepick.photo.domain.PhotoOutboxRepository;
import com.back.facepick.photo.domain.PhotoPipeline;
import com.back.facepick.photo.domain.event.PhotoUploadedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "facepick.pipeline.mode", havingValue = "event", matchIfMissing = true)
public class EventPhotoPipeline implements PhotoPipeline {
    private final PhotoOutboxRepository photoOutboxRepository;
    private final ObjectMapper objectMapper;

    // 완료와 같은 트랜잭션에 쌓아야 커밋된 사진만 빠짐없이 발행된다 (PhotoOutboxRelay 가 보낸다).
    @Override
    public void onCompleted(Photo photo, LocalDateTime now) {
        PhotoUploadedEvent event = new PhotoUploadedEvent(
                photo.getId(), photo.getAlbumId(), photo.getStorageKey(), photo.getContentType(), photo.getByteSize());
        photoOutboxRepository.save(
                PhotoOutbox.create(PhotoUploadedEvent.TOPIC, String.valueOf(photo.getAlbumId()), toJson(event), now));
    }

    // 필드가 모두 단순 값인 record 라 직렬화가 실패할 일은 없다. Jackson 2 의 checked 예외만 풀어 준다.
    private String toJson(PhotoUploadedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("사진 업로드 이벤트 직렬화 실패 photoId=" + event.photoId(), e);
        }
    }

    // 워커가 Kafka 에서 가져가므로 커밋 뒤에 할 일이 없다.
    @Override
    public void afterCommit(Long photoId) {}
}
