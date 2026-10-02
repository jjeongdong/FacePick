package com.back.facepick.photo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 부분 유일 인덱스는 엔티티에 드러나지 않으므로 스키마 계약을 SQL 로 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class PhotoPurposeMigrationTest {

    private static final String HASH = "a".repeat(64);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V12 - 용도를 주지 않은 사진은 ALBUM 이다")
    void defaultsToAlbum() {
        // given
        jdbcTemplate.update(
                """
                INSERT INTO photos (album_id, uploader_id, content_hash, byte_size, content_type, storage_key,
                                    status, created_at, modified_at)
                VALUES (1, 1, ?, 1, 'image/jpeg', 'k', 'PENDING', now(), now())
                """,
                HASH);

        // when
        String purpose = jdbcTemplate.queryForObject("SELECT purpose FROM photos WHERE album_id = 1", String.class);

        // then
        assertThat(purpose).isEqualTo("ALBUM");
    }

    @Test
    @DisplayName("V12 - 앨범 사진과 같은 파일의 셀피는 저장된다")
    void allowsSelfieWithSameFileAsAlbumPhoto() {
        // given
        insert(1L, 1L, HASH, "ALBUM");

        // when
        insert(1L, 1L, HASH, "SELFIE");

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM photos WHERE album_id = 1", Long.class))
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("V12 - 같은 앨범의 앨범 사진끼리는 같은 파일을 저장할 수 없다")
    void rejectsDuplicateAlbumPhoto() {
        // given
        insert(1L, 1L, HASH, "ALBUM");

        // when & then
        assertThatThrownBy(() -> insert(1L, 2L, HASH, "ALBUM")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V12 - 한 멤버는 앨범에 셀피를 하나만 둘 수 있다")
    void rejectsSecondSelfieOfMember() {
        // given
        insert(1L, 1L, HASH, "SELFIE");

        // when & then
        assertThatThrownBy(() -> insert(1L, 1L, "b".repeat(64), "SELFIE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(Long albumId, Long uploaderId, String hash, String purpose) {
        jdbcTemplate.update(
                """
                INSERT INTO photos (album_id, uploader_id, content_hash, byte_size, content_type, storage_key,
                                    status, created_at, modified_at, purpose)
                VALUES (?, ?, ?, 1, 'image/jpeg', ?, 'PENDING', now(), now(), ?)
                """,
                albumId,
                uploaderId,
                hash,
                "albums/" + albumId + "/originals/" + hash,
                purpose);
    }
}
