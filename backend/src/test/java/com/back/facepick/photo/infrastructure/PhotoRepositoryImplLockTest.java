package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.photo.domain.Photo;
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

// 두 트랜잭션이 실제로 커밋해야 해서 테스트마다 롤백되는 PhotoRepositoryImplQueryTest 와 나눈다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, PhotoRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class PhotoRepositoryImplLockTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final long WAIT_SECONDS = 10;
    // 두 번째 조회가 잠금에 막혀 있을 시간을 준 뒤 첫 트랜잭션을 끝낸다.
    private static final long HOLD_MILLIS = 500;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private PhotoRepositoryImpl photoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("동시 삭제 - 먼저 조회한 쪽이 지우고 커밋할 때까지 기다렸다가, 지워진 사진은 결과에서 뺀다")
    void secondDeleteSkipsPhotoDeletedByFirst() throws Exception {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Photo photo = Photo.create(1L, 1L, "a".repeat(64), 1000L, "image/jpeg");
        photo.complete(1L, 1000L, T);
        transaction.executeWithoutResult(status -> photoRepository.saveAll(List.of(photo)));
        Long photoId = photo.getId();
        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        // when
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            List<Photo> photos = photoRepository.findAllByAlbumIdAndIds(1L, List.of(photoId));
            firstRead.countDown();
            await(releaseFirst);
            photoRepository.deleteAll(photos);
        }));
        await(firstRead);
        CompletableFuture<List<Photo>> second = CompletableFuture.supplyAsync(
                () -> transaction.execute(status -> photoRepository.findAllByAlbumIdAndIds(1L, List.of(photoId))));
        Thread.sleep(HOLD_MILLIS);
        releaseFirst.countDown();
        first.get(WAIT_SECONDS, TimeUnit.SECONDS);

        // then
        assertThat(second.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEmpty();
    }

    @Test
    @DisplayName("셀피 잠금 - 같은 멤버의 두 번째 요청은 첫 트랜잭션이 끝날 때까지 기다리고, 다른 멤버는 기다리지 않는다")
    void selfieLockSerializesSameMemberOnly() throws Exception {
        // given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            photoRepository.lockSelfie(1L, 1L);
            locked.countDown();
            await(release);
        }));
        await(locked);

        // when
        CompletableFuture<Void> sameMember = CompletableFuture.runAsync(
                () -> transaction.executeWithoutResult(status -> photoRepository.lockSelfie(1L, 1L)));
        CompletableFuture<Void> otherMember = CompletableFuture.runAsync(
                () -> transaction.executeWithoutResult(status -> photoRepository.lockSelfie(1L, 2L)));
        otherMember.get(WAIT_SECONDS, TimeUnit.SECONDS);
        Thread.sleep(HOLD_MILLIS);
        boolean sameMemberFinishedWhileLocked = sameMember.isDone();
        release.countDown();
        first.get(WAIT_SECONDS, TimeUnit.SECONDS);
        sameMember.get(WAIT_SECONDS, TimeUnit.SECONDS);

        // then
        assertThat(sameMemberFinishedWhileLocked).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
