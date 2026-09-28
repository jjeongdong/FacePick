package com.back.facepick.photo.domain.event;

// 썸네일 워커(Python)가 읽는 photo.uploaded 메시지 본문. 필드 이름이 곧 워커와의 계약이다.
public record PhotoUploadedEvent(Long photoId, Long albumId, String storageKey, String contentType, Long byteSize) {
    public static final String TOPIC = "photo.uploaded";
}
