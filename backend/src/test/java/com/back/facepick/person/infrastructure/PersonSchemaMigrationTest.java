package com.back.facepick.person.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// face-worker(Python)가 쓰는 테이블이라 엔티티가 없다. 스키마 계약을 SQL 로 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class PersonSchemaMigrationTest {

    private static final int DIMENSIONS = 512;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V10 - 인물·얼굴·분석 표시를 저장하고 코사인 거리로 가까운 얼굴부터 찾는다")
    void storesFacesAndOrdersByCosineDistance() {
        // given
        Long personId = insertPerson(1L);
        Long near = insertFace(personId, 1L, vector(1.0, 0.0));
        Long far = insertFace(personId, 1L, vector(0.0, 1.0));
        jdbcTemplate.update("UPDATE persons SET cover_face_id = ? WHERE person_id = ?", near, personId);
        jdbcTemplate.update(
                "INSERT INTO face_analyses (photo_id, album_id, face_count, analyzed_at) VALUES (10, 1, 2, now())");

        // when
        List<Long> ordered = jdbcTemplate.queryForList(
                "SELECT face_id FROM faces WHERE album_id = 1 ORDER BY embedding <=> CAST(? AS vector)",
                Long.class,
                vector(0.9, 0.1));

        // then
        assertThat(ordered).containsExactly(near, far);
    }

    @Test
    @DisplayName("V10 - 없는 인물을 가리키는 얼굴은 저장할 수 없다")
    void rejectsFaceWithoutPerson() {
        // when & then
        assertThatThrownBy(() -> insertFace(999_999L, 1L, vector(1.0, 0.0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V10 - 같은 사진의 분석 표시는 한 번만 저장된다")
    void rejectsDuplicateAnalysis() {
        // given
        jdbcTemplate.update(
                "INSERT INTO face_analyses (photo_id, album_id, face_count, analyzed_at) VALUES (20, 1, 0, now())");

        // when & then
        assertThatThrownBy(
                        () -> jdbcTemplate.update(
                                "INSERT INTO face_analyses (photo_id, album_id, face_count, analyzed_at) VALUES (20, 1, 0, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V11 - 대표 얼굴을 지우면 인물의 cover_face_id 가 비워진다")
    void clearsCoverWhenCoverFaceDeleted() {
        // given
        Long personId = insertPerson(1L);
        Long faceId = insertFace(personId, 1L, vector(1.0, 0.0));
        jdbcTemplate.update("UPDATE persons SET cover_face_id = ? WHERE person_id = ?", faceId, personId);

        // when
        jdbcTemplate.update("DELETE FROM faces WHERE face_id = ?", faceId);

        // then
        Long coverFaceId = jdbcTemplate.queryForObject(
                "SELECT cover_face_id FROM persons WHERE person_id = ?", Long.class, personId);
        assertThat(coverFaceId).isNull();
    }

    private Long insertPerson(Long albumId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO persons (album_id, created_at, modified_at) VALUES (?, now(), now()) RETURNING person_id",
                Long.class,
                albumId);
    }

    private Long insertFace(Long personId, Long albumId, String embedding) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO faces (photo_id, album_id, person_id, bbox_x1, bbox_y1, bbox_x2, bbox_y2,
                                   det_score, embedding, created_at)
                VALUES (10, ?, ?, 0, 0, 50, 50, 0.9, CAST(? AS vector), now())
                RETURNING face_id
                """,
                Long.class,
                albumId,
                personId,
                embedding);
    }

    // 512차원 중 앞 두 칸만 채운 pgvector 문자열 '[a,b,0,...]'.
    private static String vector(double first, double second) {
        List<String> values = new ArrayList<>(Collections.nCopies(DIMENSIONS, "0"));
        values.set(0, String.valueOf(first));
        values.set(1, String.valueOf(second));
        return "[" + String.join(",", values) + "]";
    }
}
