package com.back.facepick.person.infrastructure;

import static com.back.facepick.person.fixture.FaceFixture.insertAnalysis;
import static com.back.facepick.person.fixture.FaceFixture.insertFace;
import static com.back.facepick.person.fixture.FaceFixture.insertPerson;
import static com.back.facepick.person.fixture.FaceFixture.setCover;
import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, PersonFaceRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class PersonFaceRepositoryImplQueryTest {

    private static final Long ALBUM_ID = 1L;
    private static final Long OTHER_ALBUM_ID = 2L;
    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private PersonFaceRepositoryImpl personFaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("인물 조회 - 이 앨범의 해당 사진에 있는 인물만 중복 없이 준다")
    void findsPersonIdsOfPhotosInAlbum() {
        // given
        Long first = insertPerson(jdbcTemplate, ALBUM_ID);
        Long second = insertPerson(jdbcTemplate, ALBUM_ID);
        Long untouched = insertPerson(jdbcTemplate, ALBUM_ID);
        Long otherAlbum = insertPerson(jdbcTemplate, OTHER_ALBUM_ID);
        insertFace(jdbcTemplate, ALBUM_ID, 10L, first, 0.9);
        insertFace(jdbcTemplate, ALBUM_ID, 10L, second, 0.9);
        insertFace(jdbcTemplate, ALBUM_ID, 11L, first, 0.9);
        insertFace(jdbcTemplate, ALBUM_ID, 12L, untouched, 0.9);
        insertFace(jdbcTemplate, OTHER_ALBUM_ID, 10L, otherAlbum, 0.9);

        // when
        List<Long> personIds = personFaceRepository.findPersonIdsOfPhotos(ALBUM_ID, List.of(10L, 11L));

        // then
        assertThat(personIds).containsExactly(first, second);
    }

    @Test
    @DisplayName("얼굴 삭제 - 이 앨범의 해당 사진 얼굴만 지우고, 대표 얼굴이었으면 cover_face_id 가 비워진다")
    void deletesFacesOfPhotosInAlbum() {
        // given
        Long person = insertPerson(jdbcTemplate, ALBUM_ID);
        Long deleted = insertFace(jdbcTemplate, ALBUM_ID, 10L, person, 0.9);
        Long kept = insertFace(jdbcTemplate, ALBUM_ID, 11L, person, 0.8);
        setCover(jdbcTemplate, person, deleted);
        Long otherPerson = insertPerson(jdbcTemplate, OTHER_ALBUM_ID);
        Long otherAlbumFace = insertFace(jdbcTemplate, OTHER_ALBUM_ID, 10L, otherPerson, 0.9);

        // when
        personFaceRepository.deleteFacesOfPhotos(ALBUM_ID, List.of(10L));

        // then
        assertThat(faceIds(ALBUM_ID)).containsExactly(kept);
        assertThat(faceIds(OTHER_ALBUM_ID)).containsExactly(otherAlbumFace);
        assertThat(coverFaceId(person)).isNull();
    }

    @Test
    @DisplayName("분석 표시 삭제 - 이 앨범의 해당 사진 분석 표시만 지운다")
    void deletesAnalysesOfPhotosInAlbum() {
        // given
        insertAnalysis(jdbcTemplate, ALBUM_ID, 10L);
        insertAnalysis(jdbcTemplate, ALBUM_ID, 11L);
        insertAnalysis(jdbcTemplate, OTHER_ALBUM_ID, 12L);

        // when
        personFaceRepository.deleteAnalysesOfPhotos(ALBUM_ID, List.of(10L, 12L));

        // then
        assertThat(jdbcTemplate.queryForList("SELECT photo_id FROM face_analyses ORDER BY photo_id", Long.class))
                .containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("인물 정리 - 대표 얼굴이 빈 인물은 남은 얼굴 중 점수가 가장 높은 얼굴로 채운다 (동점이면 face_id 작은 것)")
    void fillsEmptyCoverWithBestRemainingFace() {
        // given
        Long person = insertPerson(jdbcTemplate, ALBUM_ID);
        insertFace(jdbcTemplate, ALBUM_ID, 11L, person, 0.7);
        Long best = insertFace(jdbcTemplate, ALBUM_ID, 12L, person, 0.9);
        insertFace(jdbcTemplate, ALBUM_ID, 13L, person, 0.9);

        // when
        personFaceRepository.cleanUpPersons(List.of(person), T);

        // then
        assertThat(coverFaceId(person)).isEqualTo(best);
        assertThat(modifiedAt(person)).isEqualTo(T);
    }

    @Test
    @DisplayName("인물 정리 - 얼굴이 남지 않은 인물은 지운다")
    void deletesPersonWithoutFaces() {
        // given
        Long person = insertPerson(jdbcTemplate, ALBUM_ID);

        // when
        personFaceRepository.cleanUpPersons(List.of(person), T);

        // then
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM persons WHERE person_id = ?", Long.class, person))
                .isZero();
    }

    @Test
    @DisplayName("인물 정리 - 대표 얼굴이 있는 인물은 그대로 둔다")
    void keepsPersonWithCover() {
        // given
        Long person = insertPerson(jdbcTemplate, ALBUM_ID);
        Long cover = insertFace(jdbcTemplate, ALBUM_ID, 11L, person, 0.5);
        insertFace(jdbcTemplate, ALBUM_ID, 12L, person, 0.9);
        setCover(jdbcTemplate, person, cover);

        // when
        personFaceRepository.cleanUpPersons(List.of(person), T);

        // then
        assertThat(coverFaceId(person)).isEqualTo(cover);
        assertThat(modifiedAt(person)).isNotEqualTo(T);
    }

    @Test
    @DisplayName("앨범 잠금 - 트랜잭션 안에서 잠금을 얻는다")
    void locksAlbum() {
        // when
        personFaceRepository.lockAlbum(ALBUM_ID);

        // then
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND objid = ?",
                        Long.class,
                        ALBUM_ID))
                .isOne();
    }

    private List<Long> faceIds(Long albumId) {
        return jdbcTemplate.queryForList(
                "SELECT face_id FROM faces WHERE album_id = ? ORDER BY face_id", Long.class, albumId);
    }

    private Long coverFaceId(Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT cover_face_id FROM persons WHERE person_id = ?", Long.class, personId);
    }

    private LocalDateTime modifiedAt(Long personId) {
        return jdbcTemplate.queryForObject(
                "SELECT modified_at FROM persons WHERE person_id = ?", LocalDateTime.class, personId);
    }
}
