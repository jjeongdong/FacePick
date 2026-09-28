package com.back.facepick.photo.infrastructure;

import com.back.facepick.photo.domain.PhotoEventPublisher;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaPhotoEventPublisher implements PhotoEventPublisher {
    // Relay 가 이 시간 안에 결과를 못 받으면 다음 주기에 다시 보낸다 (중복은 워커가 멱등하게 처리).
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 전송이 중단됐습니다. topic=" + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka 전송에 실패했습니다. topic=" + topic, e);
        }
    }
}
