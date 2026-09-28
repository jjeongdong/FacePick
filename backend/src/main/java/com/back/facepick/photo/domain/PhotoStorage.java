package com.back.facepick.photo.domain;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;

// 원본은 API 서버를 거치지 않고 스토리지로 바로 간다. 도메인이 S3 SDK 를 모르게 하려고 둔 포트.
public interface PhotoStorage {
    /** contentType 을 서명에 넣어, PUT 할 때 같은 Content-Type 헤더가 있어야 올라간다. */
    URL createUploadUrl(String key, String contentType);

    Duration uploadUrlExpiry();

    Optional<Long> findObjectSize(String key);
}
