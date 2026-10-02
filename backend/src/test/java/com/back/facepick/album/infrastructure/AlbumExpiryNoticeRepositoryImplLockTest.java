package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumExpiryNotice;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 두 트랜잭션이 실제로 커밋해야 해서 테스트마다 롤백되는 QueryTest 와 나눈다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, AlbumExpiryNoticeRepositoryImpl.class, AlbumRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AlbumExpiryNoticeRepositoryImplLockTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final long WAIT_SECONDS = 10;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlbumExpiryNoticeRepositoryImpl noticeRepository;

    @Autowired
    private AlbumRepositoryImpl albumRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("동시 선점 - 한쪽이 잠근 행은 건너뛰고 나머지만 가져간다 (서버 두 대가 같은 알림을 보내지 않는다)")
    void concurrentClaimsDoNotOverlap() throws Exception {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Long> ids = transaction.execute(status -> {
            Album album = albumRepository.save(Album.create(1L, "제주 여행", NOW, "lock-code"));
            return List.of(1L, 2L, 3L).stream()
                    .map(userId -> noticeRepository
                            .save(AlbumExpiryNotice.create(album.getId(), userId, NOW))
                            .getId())
                    .toList();
        });
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        // when
        CompletableFuture<List<Long>> first = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
            List<Long> claimed = noticeRepository.findDueForUpdate(NOW, 2).stream()
                    .map(AlbumExpiryNotice::getId)
                    .toList();
            firstLocked.countDown();
            await(releaseFirst);
            return claimed;
        }));
        await(firstLocked);
        List<Long> second = transaction.execute(status -> noticeRepository.findDueForUpdate(NOW, 10).stream()
                .map(AlbumExpiryNotice::getId)
                .toList());
        releaseFirst.countDown();

        // then
        assertThat(first.get(WAIT_SECONDS, TimeUnit.SECONDS)).containsExactly(ids.get(0), ids.get(1));
        assertThat(second).containsExactly(ids.get(2));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch 대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
