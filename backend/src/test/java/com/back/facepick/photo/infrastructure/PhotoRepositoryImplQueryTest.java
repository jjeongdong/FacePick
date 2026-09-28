package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.exception.PhotoNotFoundException;
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
@Import({JpaAuditingConfig.class, PhotoRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class PhotoRepositoryImplQueryTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private PhotoRepositoryImpl photoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("해시 목록 조회 - 같은 앨범에서 목록에 있는 해시만 돌려준다")
    void findsByAlbumAndHashes() {
        // given
        photoRepository.saveAll(List.of(
                Photo.create(1L, 1L, HASH_A, 1000L, "image/jpeg"),
                Photo.create(1L, 1L, HASH_B, 1000L, "image/jpeg"),
                Photo.create(2L, 1L, HASH_A, 1000L, "image/jpeg")));
        entityManager.flush();
        entityManager.clear();

        // when
        List<Photo> photos = photoRepository.findAllByAlbumIdAndContentHashes(1L, List.of(HASH_A, HASH_C));

        // then
        assertThat(photos).extracting(Photo::getAlbumId, Photo::getContentHash).containsExactly(tuple(1L, HASH_A));
    }

    @Test
    @DisplayName("단건 조회 - 없으면 PhotoNotFoundException")
    void throwsWhenMissing() {
        // when & then
        assertThatThrownBy(() -> photoRepository.getById(999L)).isInstanceOf(PhotoNotFoundException.class);
    }
}
