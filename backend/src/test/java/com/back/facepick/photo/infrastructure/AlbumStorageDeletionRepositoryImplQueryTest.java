package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.photo.domain.AlbumStorageDeletion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, AlbumStorageDeletionRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class AlbumStorageDeletionRepositoryImplQueryTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlbumStorageDeletionRepositoryImpl repository;

    @Autowired
    private AlbumStorageDeletionJpaRepository jpaRepository;

    @Test
    @DisplayName("같은 앨범을 두 번 넣어도 한 행이고 처음 시각이 남는다")
    void saveIfAbsentKeepsFirstRow() {
        // when
        repository.saveIfAbsent(1L, T);
        repository.saveIfAbsent(1L, T.plusMinutes(5));

        // then
        List<AlbumStorageDeletion> rows = jpaRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getCreatedAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("기준 시각 이전(같은 시각 포함)이고 아직 안 지운 행만 오래된 순으로 limit 개")
    void findsDueRows() {
        // given
        repository.saveIfAbsent(11L, T.minusMinutes(1));
        repository.saveIfAbsent(12L, T.minusMinutes(3));
        repository.saveIfAbsent(13L, T);
        repository.saveIfAbsent(14L, T.plusSeconds(1));
        repository.saveIfAbsent(15L, T.minusMinutes(5));
        AlbumStorageDeletion done = jpaRepository.findById(15L).orElseThrow();
        done.markDeleted(T);
        jpaRepository.flush();

        // when
        List<AlbumStorageDeletion> due = repository.findDue(T, 2);

        // then
        assertThat(due).extracting(AlbumStorageDeletion::getAlbumId).containsExactly(12L, 11L);
    }
}
