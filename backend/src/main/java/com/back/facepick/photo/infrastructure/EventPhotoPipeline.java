package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoOutbox;
import com.back.facepick.photo.domain.PhotoOutboxRepository;
import com.back.facepick.photo.domain.PhotoPipeline;
import com.back.facepick.photo.domain.event.PhotoUploadedEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "facepick.pipeline.mode", havingValue = "event", matchIfMissing = true)
public class EventPhotoPipeline implements PhotoPipeline {
    private final PhotoOutboxRepository photoOutboxRepository;
    private final JsonMapper jsonMapper;

    // 완료와 같은 트랜잭션에 쌓아야 커밋된 사진만 빠짐없이 발행된다 (PhotoOutboxRelay 가 보낸다).
    @Override
    public void onCompleted(Photo photo, LocalDateTime now) {
        PhotoUploadedEvent event = new PhotoUploadedEvent(
                photo.getId(), photo.getAlbumId(), photo.getStorageKey(), photo.getContentType(), photo.getByteSize());
        photoOutboxRepository.save(PhotoOutbox.create(
                PhotoUploadedEvent.TOPIC,
                String.valueOf(photo.getAlbumId()),
                jsonMapper.writeValueAsString(event),
                now));
    }

    // 워커가 Kafka 에서 가져가므로 커밋 뒤에 할 일이 없다.
    @Override
    public void afterCommit(Long photoId) {}
}
