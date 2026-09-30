package com.back.facepick.album.application;

import com.back.facepick.album.domain.AlbumExpiryNoticeRepository;
import com.back.facepick.global.config.scheduling.SchedulerNames;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// PRD: 앨범은 30일 뒤 삭제되고 7일 전에 멤버에게 알린다. 발송은 AlbumExpiryNoticeSender 가 따로 한다.
// 스캔을 반복해도 (앨범, 멤버) 유일 키로 한 번만 만들어져, 그 사이 들어온 멤버만 새로 잡힌다.
@Slf4j
@Service
public class AlbumExpiryNoticeScanner {
    private final AlbumExpiryNoticeRepository albumExpiryNoticeRepository;
    private final int daysBefore;

    public AlbumExpiryNoticeScanner(
            AlbumExpiryNoticeRepository albumExpiryNoticeRepository,
            @Value("${facepick.album.expiry-notice.days-before}") int daysBefore) {
        this.albumExpiryNoticeRepository = albumExpiryNoticeRepository;
        this.daysBefore = daysBefore;
    }

    @Scheduled(
            fixedDelayString = "${facepick.album.expiry-notice.scan-interval-millis}",
            scheduler = SchedulerNames.EXTERNAL)
    @Transactional
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        int created = albumExpiryNoticeRepository.createPendingForAlbumsExpiringBetween(now, now.plusDays(daysBefore));
        if (created > 0) {
            log.info("앨범 만료 알림 {}건을 대기열에 넣었다", created);
        }
    }
}
