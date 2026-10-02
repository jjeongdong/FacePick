package com.back.facepick.photo.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.photo.domain.PhotoStorage;
import com.back.facepick.photo.infrastructure.AlbumStorageDeletionRepositoryImpl;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 단위 테스트(AlbumStorageCleanerTest)는 흐름만 본다. 여기서는 실제 트랜잭션으로, 앨범 하나에 수만 번 걸리는
// 스토리지 호출이 트랜잭션 밖에서 일어나는지, 앨범마다 지운 기록이 바로 커밋되는지 본다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
    JpaAuditingConfig.class,
    AlbumStorageDeletionRepositoryImpl.class,
    AlbumStorageCleaner.class,
    AlbumStorageCleanerIntegrationTest.FakeStorageConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AlbumStorageCleanerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlbumStorageCleaner cleaner;

    @Autowired
    private RecordingStorage storage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("스토리지 삭제는 트랜잭션 밖에서 하고, 앞 앨범의 지운 기록은 다음 앨범을 지우기 전에 이미 커밋돼 있다")
    void deletesOutsideTransactionAndCommitsEachRow() {
        // given
        LocalDateTime old = LocalDateTime.now().minusHours(1);
        jdbcTemplate.update(
                "INSERT INTO album_storage_deletions (album_id, created_at) VALUES (301, ?), (302, ?)",
                old.minusMinutes(1),
                old);
        storage.onDelete = prefix -> {
            storage.transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (prefix.equals("albums/302/")) {
                // 다른 커넥션으로 읽으므로 커밋된 값만 보인다.
                storage.firstDeletedAtSeenDuringSecond = jdbcTemplate.queryForObject(
                        "SELECT deleted_at IS NOT NULL FROM album_storage_deletions WHERE album_id = 301",
                        Boolean.class);
            }
        };

        // when
        cleaner.clean();

        // then
        assertThat(storage.transactionActive).containsExactly(false, false);
        assertThat(storage.firstDeletedAtSeenDuringSecond).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM album_storage_deletions WHERE album_id IN (301, 302) AND deleted_at IS NOT NULL",
                        Long.class))
                .isEqualTo(2L);
    }

    @TestConfiguration
    static class FakeStorageConfig {
        @Bean
        RecordingStorage recordingStorage() {
            return new RecordingStorage();
        }
    }

    static class RecordingStorage implements PhotoStorage {
        final List<Boolean> transactionActive = new ArrayList<>();
        Consumer<String> onDelete = prefix -> {};
        Boolean firstDeletedAtSeenDuringSecond;

        @Override
        public void deleteByPrefix(String prefix) {
            onDelete.accept(prefix);
        }

        @Override
        public URL createUploadUrl(String key, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Duration uploadUrlExpiry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Long> findObjectSize(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URL createDownloadUrl(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public URL createDownloadUrl(String key, String fileName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Duration downloadUrlExpiry() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteObjects(List<String> keys) {
            throw new UnsupportedOperationException();
        }
    }
}
