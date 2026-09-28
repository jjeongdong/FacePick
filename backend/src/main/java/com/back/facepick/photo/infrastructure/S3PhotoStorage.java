package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.PhotoStorage;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
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
