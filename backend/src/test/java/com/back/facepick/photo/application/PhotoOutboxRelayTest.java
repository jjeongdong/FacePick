package com.back.facepick.photo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;

import com.back.facepick.photo.domain.PhotoEventPublisher;
import com.back.facepick.photo.domain.PhotoOutbox;
import com.back.facepick.photo.domain.PhotoOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhotoOutboxRelayTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Mock
    private PhotoOutboxRepository photoOutboxRepository;

    @Mock
    private PhotoEventPublisher photoEventPublisher;

    @InjectMocks
    private PhotoOutboxRelay photoOutboxRelay;

    @Test
    @DisplayName("보낸 행마다 발행 시각을 기록한다")
    void marksPublishedRows() {
        // given
        PhotoOutbox first = PhotoOutbox.create("photo.uploaded", "1", "{\"n\":1}", NOW);
        PhotoOutbox second = PhotoOutbox.create("photo.uploaded", "2", "{\"n\":2}", NOW);
        given(photoOutboxRepository.findUnpublished(100)).willReturn(List.of(first, second));

        // when
        photoOutboxRelay.relay();

        // then
        then(photoEventPublisher).should().publish("photo.uploaded", "1", "{\"n\":1}");
        then(photoEventPublisher).should().publish("photo.uploaded", "2", "{\"n\":2}");
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("전송이 실패하면 그 행에서 멈추고 앞선 성공분만 표시한다")
    void stopsAtFirstFailure() {
        // given
        PhotoOutbox first = PhotoOutbox.create("photo.uploaded", "1", "{\"n\":1}", NOW);
        PhotoOutbox second = PhotoOutbox.create("photo.uploaded", "1", "{\"n\":2}", NOW);
        PhotoOutbox third = PhotoOutbox.create("photo.uploaded", "1", "{\"n\":3}", NOW);
        given(photoOutboxRepository.findUnpublished(100)).willReturn(List.of(first, second, third));
        // 인자가 다른 호출마다 strict stubs 경고가 예외로 나지 않게, 두 번째 본문에서만 실패시킨다.
        willAnswer(invocation -> {
                    if ("{\"n\":2}".equals(invocation.getArgument(2))) {
                        throw new IllegalStateException("broker down");
                    }
                    return null;
                })
                .given(photoEventPublisher)
                .publish(anyString(), anyString(), anyString());

        // when
        photoOutboxRelay.relay();

        // then
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNull();
        assertThat(third.getPublishedAt()).isNull();
        then(photoEventPublisher).should(never()).publish("photo.uploaded", "1", "{\"n\":3}");
    }
}
