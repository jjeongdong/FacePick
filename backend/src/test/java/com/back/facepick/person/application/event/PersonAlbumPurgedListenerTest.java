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
import com.back.facepick.photo.domain.event.AlbumPhotosPurgedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 커밋 시점 동작을 봐야 해서 테스트 트랜잭션을 쓰지 않는다. 행이 테스트 사이에 남으므로 테스트마다 다른 앨범 ID 를 쓴다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
    JpaAuditingConfig.class,
    PersonFaceRepositoryImpl.class,
    PersonCommandService.class,
    PersonAlbumPurgedListener.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class PersonAlbumPurgedListenerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
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
    @DisplayName("사진 정리 트랜잭션에서 이벤트를 발행하면 커밋과 함께 그 앨범의 얼굴 데이터가 모두 파기된다")
    void purgesOnCommit() {
        // given
        Long albumId = 201L;
        Long person = insertPerson(jdbcTemplate, albumId);
        setCover(jdbcTemplate, person, insertFace(jdbcTemplate, albumId, 2001L, person, 0.9));
        insertAnalysis(jdbcTemplate, albumId, 2001L);

        // when
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> eventPublisher.publishEvent(new AlbumPhotosPurgedEvent(albumId)));

        // then
        assertThat(count("faces", albumId)).isZero();
        assertThat(count("persons", albumId)).isZero();
        assertThat(count("face_analyses", albumId)).isZero();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 IllegalTransactionStateException 이고 아무것도 지우지 않는다")
    void requiresTransaction() {
        // given
        Long albumId = 202L;
        Long person = insertPerson(jdbcTemplate, albumId);
        setCover(jdbcTemplate, person, insertFace(jdbcTemplate, albumId, 2002L, person, 0.9));

        // when & then
        assertThatThrownBy(() -> personCommandService.purgeAlbum(albumId))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count("faces", albumId)).isOne();
    }

    private Long count(String table, Long albumId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE album_id = ?", Long.class, albumId);
    }
}
