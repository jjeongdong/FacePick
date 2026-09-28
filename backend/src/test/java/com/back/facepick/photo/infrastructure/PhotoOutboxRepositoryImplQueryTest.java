package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.photo.domain.PhotoOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
@Import({JpaAuditingConfig.class, PhotoOutboxRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class PhotoOutboxRepositoryImplQueryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private PhotoOutboxRepositoryImpl photoOutboxRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("발행 전 조회 - 발행된 행은 빼고 기록 순서대로 limit 개만")
    void findsUnpublishedInOrder() {
        // given
        PhotoOutbox published = photoOutboxRepository.save(PhotoOutbox.create("photo.uploaded", "1", "{\"n\":1}", NOW));
        published.markPublished(NOW);
        photoOutboxRepository.save(PhotoOutbox.create("photo.uploaded", "1", "{\"n\":2}", NOW));
        photoOutboxRepository.save(PhotoOutbox.create("photo.uploaded", "1", "{\"n\":3}", NOW));
        photoOutboxRepository.save(PhotoOutbox.create("photo.uploaded", "1", "{\"n\":4}", NOW));
        entityManager.flush();
        entityManager.clear();

        // when
        List<PhotoOutbox> unpublished = photoOutboxRepository.findUnpublished(2);

        // then
        assertThat(unpublished).extracting(PhotoOutbox::getPayload).containsExactly("{\"n\":2}", "{\"n\":3}");
    }
}
