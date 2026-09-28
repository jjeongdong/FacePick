package com.back.facepick.photo.infrastructure;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// 지금은 photo 만 쓰므로 여기에 둔다. 다른 BC 가 스토리지를 쓰게 되면 global 로 옮긴다.
@Configuration
public class S3StorageConfig {

    // SeaweedFS·R2 는 버킷 이름을 호스트가 아닌 경로에 둬야 한다 (path-style).
    @Bean
    public S3Client s3Client(
            @Value("${storage.endpoint}") String endpoint,
            @Value("${storage.region}") String region,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials(accessKey, secretKey))
                .forcePathStyle(true)
                // SDK 기본 체크섬 헤더를 S3 호환 스토리지가 모두 지원하지는 않아 필요할 때만 붙인다.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .build();
    }

    // 서명 URL 은 앱·워커가 접속하는 주소(public-endpoint)로 만들어야 서명이 맞는다.
    @Bean
    public S3Presigner s3Presigner(
            @Value("${storage.public-endpoint}") String publicEndpoint,
            @Value("${storage.region}") String region,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(publicEndpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials(accessKey, secretKey))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static StaticCredentialsProvider credentials(String accessKey, String secretKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
