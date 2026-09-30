package com.back.facepick.photo.infrastructure;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "facepick.pipeline.mode", havingValue = "sync")
public class SyncPhotoPipelineConfig {

    // 얼굴 분석이 썸네일보다 오래 걸려 읽기 타임아웃을 워커마다 따로 둔다.
    @Bean
    public SyncPhotoPipeline syncPhotoPipeline(
            @Value("${facepick.pipeline.thumbnail-url}") String thumbnailUrl,
            @Value("${facepick.pipeline.face-url}") String faceUrl,
            @Value("${facepick.pipeline.connect-timeout-millis}") long connectTimeoutMillis,
            @Value("${facepick.pipeline.thumbnail-read-timeout-millis}") long thumbnailReadTimeoutMillis,
            @Value("${facepick.pipeline.face-read-timeout-millis}") long faceReadTimeoutMillis) {
        return new SyncPhotoPipeline(
                restClient(thumbnailUrl, connectTimeoutMillis, thumbnailReadTimeoutMillis),
                restClient(faceUrl, connectTimeoutMillis, faceReadTimeoutMillis));
    }

    private static RestClient restClient(String baseUrl, long connectTimeoutMillis, long readTimeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
