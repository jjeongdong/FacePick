package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.PhotoPipeline;
import com.back.facepick.photo.domain.exception.PhotoNotFoundException;
import com.back.facepick.photo.domain.exception.PhotoProcessingFailedException;
import com.back.facepick.photo.domain.exception.PhotoProcessingUnavailableException;
import java.time.LocalDateTime;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 이벤트 방식과 비교 측정하기 위한 동기 방식: 완료 요청 안에서 thumbnail → face 워커를 차례로 HTTP 로 부른다.
public class SyncPhotoPipeline implements PhotoPipeline {
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;

    private final RestClient thumbnailClient;
    private final RestClient faceClient;

    public SyncPhotoPipeline(RestClient thumbnailClient, RestClient faceClient) {
        this.thumbnailClient = thumbnailClient;
        this.faceClient = faceClient;
    }

    // 워커는 커밋된 행만 읽을 수 있어 트랜잭션 안에서는 할 일이 없다.
    @Override
    public void onCompleted(Photo photo, LocalDateTime now) {}

    // 끝난 단계는 워커가 멱등하게 건너뛰므로, 재시도 요청에도 두 단계를 모두 다시 부른다.
    @Override
    public void afterCommit(Long photoId) {
        try {
            Processed processed = thumbnailClient
                    .post()
                    .uri("/process")
                    .body(new ProcessBody(photoId))
                    .retrieve()
                    .onStatus(status -> status.value() == NOT_FOUND, (request, response) -> {
                        throw new PhotoNotFoundException();
                    })
                    .onStatus(status -> status.value() == UNPROCESSABLE, (request, response) -> {
                        throw new PhotoProcessingFailedException();
                    })
                    .body(Processed.class);
            faceClient
                    .post()
                    .uri("/analyze")
                    .body(new AnalyzeBody(processed.photoId(), processed.albumId(), processed.previewKey()))
                    .retrieve()
                    .onStatus(status -> status.value() == UNPROCESSABLE, (request, response) -> {
                        throw new PhotoProcessingFailedException();
                    })
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // 던지는 곳에서는 로그를 남기지 않는다. 원인을 붙여 두면 GlobalExceptionHandler 가 한 번에 남긴다.
            PhotoProcessingUnavailableException unavailable = new PhotoProcessingUnavailableException();
            unavailable.initCause(e);
            throw unavailable;
        }
    }

    record ProcessBody(Long photoId) {}

    record Processed(Long photoId, Long albumId, String previewKey, Integer width, Integer height) {}

    record AnalyzeBody(Long photoId, Long albumId, String previewKey) {}
}
