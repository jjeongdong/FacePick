package com.back.facepick.person.application.event;

import static com.back.facepick.person.fixture.FaceFixture.insertAnalysis;
import static com.back.facepick.person.fixture.FaceFixture.insertFace;
import static com.back.facepick.person.fixture.FaceFixture.insertPerson;
import static com.back.facepick.person.fixture.FaceFixture.setCover;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.person.application.PersonCommandService;
import com.back.facepick.person.infrastructure.PersonFaceRepositoryImpl;
import com.back.facepick.photo.domain.event.PhotosDeletedEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// 커밋 시점 동작과 두 트랜잭션의 잠금 대기를 봐야 해서 테스트 트랜잭션을 쓰지 않는다.
// 행이 테스트 사이에 남으므로 테스트마다 다른 앨범 ID 를 쓴다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
    JpaAuditingConfig.class,
    PersonFaceRepositoryImpl.class,
    PersonCommandService.class,
    PersonPhotosDeletedListener.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class PersonPhotosDeletedListenerTest {

    private static final long WAIT_SECONDS = 10;
    // 삭제 트랜잭션이 잠금에 막혀 있을 시간을 준 뒤 잠금을 푼다.
    private static final long HOLD_MILLIS = 500;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PersonCommandService personCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("사진 삭제 트랜잭션에서 이벤트를 발행하면 커밋과 함께 얼굴·분석 표시·빈 인물이 정리된다")
    void cleansUpOnCommit() {
        // given
        Long albumId = 101L;
        Long removed = insertPerson(jdbcTemplate, albumId);
        setCover(jdbcTemplate, removed, insertFace(jdbcTemplate, albumId, 1001L, removed, 0.9));
        Long kept = insertPerson(jdbcTemplate, albumId);
        Long oldCover = insertFace(jdbcTemplate, albumId, 1001L, kept, 0.9);
        Long remaining = insertFace(jdbcTemplate, albumId, 1002L, kept, 0.8);
        setCover(jdbcTemplate, kept, oldCover);
        insertAnalysis(jdbcTemplate, albumId, 1001L);

        // when
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> eventPublisher.publishEvent(new PhotosDeletedEvent(albumId, List.of(1001L))));

        // then
        assertThat(count("SELECT count(*) FROM faces WHERE photo_id = 1001")).isZero();
        assertThat(count("SELECT count(*) FROM face_analyses WHERE photo_id = 1001"))
                .isZero();
        assertThat(count("SELECT count(*) FROM persons WHERE person_id = " + removed))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT cover_face_id FROM persons WHERE person_id = ?", Long.class, kept))
                .isEqualTo(remaining);
    }

    @Test
    @DisplayName("face-worker 가 앨범 잠금을 쥐고 얼굴을 저장하는 중이면 기다렸다가 방금 저장된 얼굴까지 지운다")
    void waitsForWorkerAndDeletesFaceItSaved() throws Exception {
        // given
        Long albumId = 102L;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> worker = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(?)", Integer.class, albumId);
            locked.countDown();
            await(release);
            Long person = insertPerson(jdbcTemplate, albumId);
            setCover(jdbcTemplate, person, insertFace(jdbcTemplate, albumId, 2001L, person, 0.9));
            insertAnalysis(jdbcTemplate, albumId, 2001L);
        }));
        await(locked);

        // when
        CompletableFuture<Void> deletion = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(
                status -> eventPublisher.publishEvent(new PhotosDeletedEvent(albumId, List.of(2001L)))));
        Thread.sleep(HOLD_MILLIS);
        boolean finishedWhileLocked = deletion.isDone();
        release.countDown();
        worker.get(WAIT_SECONDS, TimeUnit.SECONDS);
        deletion.get(WAIT_SECONDS, TimeUnit.SECONDS);

        // then
        assertThat(finishedWhileLocked).isFalse();
        assertThat(count("SELECT count(*) FROM faces WHERE album_id = " + albumId))
                .isZero();
        assertThat(count("SELECT count(*) FROM face_analyses WHERE album_id = " + albumId))
                .isZero();
        assertThat(count("SELECT count(*) FROM persons WHERE album_id = " + albumId))
                .isZero();
    }

    @Test
    @DisplayName("같은 앨범의 다른 사진을 동시에 지워도 둘 다 끝나고 두 사진의 얼굴이 모두 정리된다")
    void concurrentDeletionsInSameAlbumBothFinish() throws Exception {
        // given
        Long albumId = 103L;
        Long person = insertPerson(jdbcTemplate, albumId);
        setCover(jdbcTemplate, person, insertFace(jdbcTemplate, albumId, 3001L, person, 0.9));
        insertFace(jdbcTemplate, albumId, 3002L, person, 0.8);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // when
        CompletableFuture<Void> first = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(
                status -> eventPublisher.publishEvent(new PhotosDeletedEvent(albumId, List.of(3001L)))));
        CompletableFuture<Void> second = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(
                status -> eventPublisher.publishEvent(new PhotosDeletedEvent(albumId, List.of(3002L)))));
        first.get(WAIT_SECONDS, TimeUnit.SECONDS);
        second.get(WAIT_SECONDS, TimeUnit.SECONDS);

        // then
        assertThat(count("SELECT count(*) FROM faces WHERE album_id = " + albumId))
                .isZero();
        assertThat(count("SELECT count(*) FROM persons WHERE album_id = " + albumId))
                .isZero();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 IllegalTransactionStateException 이고 아무것도 지우지 않는다")
    void requiresTransaction() {
        // given
        Long albumId = 104L;
        Long person = insertPerson(jdbcTemplate, albumId);
        setCover(jdbcTemplate, person, insertFace(jdbcTemplate, albumId, 4001L, person, 0.9));

        // when & then
        assertThatThrownBy(() -> personCommandService.deletePhotoFaces(albumId, List.of(4001L)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count("SELECT count(*) FROM faces WHERE album_id = " + albumId))
                .isOne();
    }

    private Long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
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
