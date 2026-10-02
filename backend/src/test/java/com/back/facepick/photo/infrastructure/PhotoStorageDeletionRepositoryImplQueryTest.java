package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.photo.domain.PhotoStorageDeletion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, PhotoStorageDeletionRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class PhotoStorageDeletionRepositoryImplQueryTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private PhotoStorageDeletionRepositoryImpl photoStorageDeletionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("지울 때가 된 행 조회 - 아직 안 지운 행만, 오래된 순으로 limit 개")
    void findsDueRowsOldestFirst() {
        // given
        PhotoStorageDeletion oldest = PhotoStorageDeletion.create("albums/1/originals/a", T.minusMinutes(30));
        PhotoStorageDeletion middle = PhotoStorageDeletion.create("albums/1/originals/e", T.minusMinutes(25));
        PhotoStorageDeletion newest = PhotoStorageDeletion.create("albums/1/originals/b", T.minusMinutes(20));
        PhotoStorageDeletion done = PhotoStorageDeletion.create("albums/1/originals/d", T.minusMinutes(40));
        done.markDeleted(T.minusMinutes(10));
        photoStorageDeletionRepository.saveAll(List.of(newest, done, oldest, middle));
        entityManager.flush();
        entityManager.clear();

        // when
        List<PhotoStorageDeletion> rows = photoStorageDeletionRepository.findDue(T.minusMinutes(20), 2);

        // then
        assertThat(rows)
                .extracting(PhotoStorageDeletion::getStorageKey)
                .containsExactly("albums/1/originals/a", "albums/1/originals/e");
    }

    @Test
    @DisplayName("지울 때가 된 행 조회 - 기준 시각과 같은 행은 포함하고 1분 늦은 행은 뺀다")
    void includesBoundary() {
        // given
        photoStorageDeletionRepository.saveAll(List.of(
                PhotoStorageDeletion.create("albums/1/originals/b", T.minusMinutes(20)),
                PhotoStorageDeletion.create("albums/1/originals/c", T.minusMinutes(19))));
        entityManager.flush();
        entityManager.clear();

        // when
        List<PhotoStorageDeletion> rows = photoStorageDeletionRepository.findDue(T.minusMinutes(20), 100);

        // then
        assertThat(rows).extracting(PhotoStorageDeletion::getStorageKey).containsExactly("albums/1/originals/b");
    }
}
