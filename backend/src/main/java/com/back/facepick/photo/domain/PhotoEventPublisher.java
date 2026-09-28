package com.back.facepick.photo.domain;

// 도메인이 Kafka 를 모르게 하려고 둔 포트. 전송이 끝날 때까지 기다리고, 실패하면 예외를 던진다.
public interface PhotoEventPublisher {
    void publish(String topic, String key, String payload);
}
