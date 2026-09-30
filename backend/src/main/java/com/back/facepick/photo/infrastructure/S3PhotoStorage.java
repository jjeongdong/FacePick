package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.PhotoStorage;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
public class S3PhotoStorage implements PhotoStorage {
    private static final int NOT_FOUND = 404;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration uploadUrlExpiry;
    private final Duration downloadUrlExpiry;

    public S3PhotoStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.presign-minutes}") long presignMinutes,
            @Value("${storage.download-url-minutes}") long downloadUrlMinutes) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.uploadUrlExpiry = Duration.ofMinutes(presignMinutes);
        this.downloadUrlExpiry = Duration.ofMinutes(downloadUrlMinutes);
    }

    @Override
    public URL createUploadUrl(String key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(uploadUrlExpiry)
                .putObjectRequest(putObjectRequest)
                .build();
        return s3Presigner.presignPutObject(presignRequest).url();
    }

    @Override
    public Duration uploadUrlExpiry() {
        return uploadUrlExpiry;
    }

    @Override
    public URL createDownloadUrl(String key) {
        return presignGet(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    // 파일명은 서버가 만든 값(facepick-{id}.{ext})이라 따옴표 이스케이프가 필요 없다.
    @Override
    public URL createDownloadUrl(String key, String fileName) {
        return presignGet(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition("attachment; filename=\"" + fileName + "\"")
                .build());
    }

    @Override
    public Duration downloadUrlExpiry() {
        return downloadUrlExpiry;
    }

    @Override
    public Optional<Long> findObjectSize(String key) {
        try {
            return Optional.of(
                    s3Client.headObject(request -> request.bucket(bucket).key(key))
                            .contentLength());
        } catch (S3Exception e) {
            // HEAD 응답은 본문이 없어 404 를 상태 코드로만 구분한다.
            if (e.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    // 여러 키를 한 번에 지우는 DeleteObjects 는 체크섬 헤더가 필수라, S3 호환 스토리지와 SDK 기본 체크섬이
    // 맞지 않을 수 있어 키마다 지운다. 없는 키도 S3 는 204 로 답한다.
    @Override
    public void deleteObjects(List<String> keys) {
        for (String key : keys) {
            s3Client.deleteObject(request -> request.bucket(bucket).key(key));
        }
    }

    // 지우면서 continuation token 으로 이어 읽으면 스토리지에 따라 키를 건너뛸 수 있어, 첫 페이지를 지우고 다시 읽는다.
    // 삭제가 조용히 실패해 같은 첫 페이지가 또 나오면 무한 반복하지 않게 예외로 끝낸다 (다음 주기에 다시).
    @Override
    public void deleteByPrefix(String prefix) {
        if (!prefix.endsWith("/")) {
            throw new IllegalArgumentException("prefix 는 '/' 로 끝나야 한다: " + prefix);
        }
        List<String> previous = List.of();
        while (true) {
            List<String> keys =
                    s3Client.listObjectsV2(request -> request.bucket(bucket).prefix(prefix)).contents().stream()
                            .map(S3Object::key)
                            .toList();
            if (keys.isEmpty()) {
                return;
            }
            if (keys.equals(previous)) {
                throw new IllegalStateException("지운 파일이 다시 조회된다 prefix=" + prefix);
            }
            deleteObjects(keys);
            previous = keys;
        }
    }

    private URL presignGet(GetObjectRequest getObjectRequest) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(downloadUrlExpiry)
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url();
    }

    // infra/docker-compose.yml 약속: 버킷은 백엔드가 시작할 때 없으면 만든다.
    @EventListener(ApplicationReadyEvent.class)
    public void createBucketIfMissing() {
        try {
            s3Client.headBucket(request -> request.bucket(bucket));
        } catch (S3Exception e) {
            if (e.statusCode() != NOT_FOUND) {
                throw e;
            }
            s3Client.createBucket(request -> request.bucket(bucket));
        }
    }
}
