package com.back.facepick.photo.application;

import com.back.facepick.global.config.scheduling.SchedulerNames;
import com.back.facepick.photo.domain.PhotoEventPublisher;
import com.back.facepick.photo.domain.PhotoOutbox;
import com.back.facepick.photo.domain.PhotoOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Transactional 은 application 에만 둘 수 있고 infrastructure 는 application 을 부를 수 없어 스케줄러를 여기에 둔다.
// 동기 방식은 outbox 를 쓰지 않으므로 이벤트 방식에서만 발행기를 띄운다.
@ConditionalOnProperty(name = "facepick.pipeline.mode", havingValue = "event", matchIfMissing = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoOutboxRelay {
    private static final int BATCH_SIZE = 100;

    private final PhotoOutboxRepository photoOutboxRepository;
    private final PhotoEventPublisher photoEventPublisher;

    // 실패한 행에서 멈춰야 같은 앨범(같은 키) 메시지 순서가 뒤바뀌지 않는다.
    @Scheduled(fixedDelay = 1000, scheduler = SchedulerNames.CORE)
    @Transactional
    public void relay() {
        for (PhotoOutbox outbox : photoOutboxRepository.findUnpublished(BATCH_SIZE)) {
            try {
                photoEventPublisher.publish(outbox.getTopic(), outbox.getMessageKey(), outbox.getPayload());
            } catch (RuntimeException e) {
                log.error("outbox 발행 실패, 다음 주기에 다시 보낸다 outboxId={}", outbox.getId(), e);
                return;
            }
            outbox.markPublished(LocalDateTime.now());
        }
    }
}
