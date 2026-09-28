package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class KafkaPhotoEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private KafkaPhotoEventPublisher publisher;

    @Test
    @DisplayName("토픽·키·본문 그대로 보내고 결과를 기다린다")
    void sendsAndWaits() {
        // given
        given(kafkaTemplate.send("photo.uploaded", "10", "{}")).willReturn(CompletableFuture.completedFuture(null));

        // when
        publisher.publish("photo.uploaded", "10", "{}");

        // then
        then(kafkaTemplate).should().send("photo.uploaded", "10", "{}");
    }

    @Test
    @DisplayName("전송이 실패하면 IllegalStateException 으로 알린다")
    void throwsWhenSendFails() {
        // given
        given(kafkaTemplate.send("photo.uploaded", "10", "{}"))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        // when & then
        assertThatThrownBy(() -> publisher.publish("photo.uploaded", "10", "{}"))
                .isInstanceOf(IllegalStateException.class);
    }
}
