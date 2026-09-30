package com.back.facepick.photo.application;

import static com.back.facepick.person.fixture.FaceFixture.insertAnalysis;
import static com.back.facepick.person.fixture.FaceFixture.insertFace;
import static com.back.facepick.person.fixture.FaceFixture.insertPerson;
import static com.back.facepick.person.fixture.FaceFixture.setCover;
import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.album.application.AlbumCommandService;
import com.back.facepick.album.application.AlbumQueryApi;
import com.back.facepick.album.application.event.AlbumPhotosPurgedListener;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumExpiryNotice;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.album.infrastructure.AlbumExpiryNoticeRepositoryImpl;
import com.back.facepick.album.infrastructure.AlbumMemberRepositoryImpl;
import com.back.facepick.album.infrastructure.AlbumRepositoryImpl;
import com.back.facepick.album.infrastructure.SecureRandomInviteCodeGenerator;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.person.application.PersonCommandService;
import com.back.facepick.person.application.event.PersonAlbumPurgedListener;
import com.back.facepick.person.application.event.PersonPhotosDeletedListener;
import com.back.facepick.person.infrastructure.PersonFaceRepositoryImpl;
import com.back.facepick.photo.domain.Photo;
import com.back.facepick.photo.domain.event.AlbumPhotosPurgedEvent;
import com.back.facepick.photo.infrastructure.AlbumStorageDeletionRepositoryImpl;
import com.back.facepick.photo.infrastructure.PhotoRepositoryImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// photo·album·person 세 BC 가 실제 트랜잭션·이벤트로 만료 앨범을 끝까지 지우는지 본다.
// 조각마다 커밋돼야 하므로 테스트 트랜잭션을 쓰지 않고, 행이 테스트 사이에 남으므로 테스트마다 새 앨범을 쓴다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
    JpaAuditingConfig.class,
    PhotoRepositoryImpl.class,
    AlbumStorageDeletionRepositoryImpl.class,
    AlbumPurgeService.class,
    AlbumPurgeScheduler.class,
    AlbumRepositoryImpl.class,
    AlbumMemberRepositoryImpl.class,
    AlbumExpiryNoticeRepositoryImpl.class,
    SecureRandomInviteCodeGenerator.class,
    AlbumQueryApi.class,
    AlbumCommandService.class,
    AlbumPhotosPurgedListener.class,
    PersonFaceRepositoryImpl.class,
    PersonCommandService.class,
    PersonPhotosDeletedListener.class,
    PersonAlbumPurgedListener.class,
    AlbumPurgeIntegrationTest.FailingListenerConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AlbumPurgeIntegrationTest {

    private static final int PHOTO_COUNT = 1200;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlbumPurgeScheduler scheduler;

    @Autowired
    private AlbumPurgeService albumPurgeService;

    @Autowired
    private PhotoRepositoryImpl photoRepository;

    @Autowired
    private AlbumRepositoryImpl albumRepository;

    @Autowired
    private AlbumMemberRepositoryImpl albumMemberRepository;

    @Autowired
    private AlbumExpiryNoticeRepositoryImpl noticeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void resetFailure() {
        FailingListenerConfig.failForAlbumId = null;
    }

    @Test
    @DisplayName("만료 앨범의 사진(앨범·셀피·PENDING)·얼굴·인물·분석·멤버·알림·앨범 행이 모두 지워지고 스토리지 정리가 예약된다")
    void deletesEverything() {
        // given
        Long albumId = givenAlbumWithData(LocalDateTime.now().minusDays(1));
        Long keptAlbumId = givenAlbumWithData(LocalDateTime.now().plusDays(1));
        // 사진 행 없이 남은 고아 얼굴 (옛 코드·경합으로 생길 수 있다)
        Long orphan = insertPerson(jdbcTemplate, albumId);
        setCover(jdbcTemplate, orphan, insertFace(jdbcTemplate, albumId, 99_999_999L, orphan, 0.9));

        // when
        scheduler.purge();

        // then
        for (String table : List.of(
                "photos", "faces", "persons", "face_analyses", "albums", "album_members", "album_expiry_notices")) {
            assertThat(count(table, albumId)).as(table).isZero();
        }
        assertThat(count("album_storage_deletions", albumId)).isOne();
        assertThat(count("photos", keptAlbumId)).isEqualTo(PHOTO_COUNT + 1);
        assertThat(count("faces", keptAlbumId)).isPositive();
        assertThat(count("albums", keptAlbumId)).isOne();
        assertThat(count("album_storage_deletions", keptAlbumId)).isZero();
    }

    @Test
    @DisplayName("사진이 한 장도 없는 만료 앨범도 바로 지운다")
    void deletesEmptyExpiredAlbum() {
        // given
        Long albumId = saveAlbum(LocalDateTime.now().minusDays(1)).getId();

        // when
        scheduler.purge();

        // then
        assertThat(count("albums", albumId)).isZero();
        assertThat(count("album_storage_deletions", albumId)).isOne();
    }

    @Test
    @DisplayName("앞서 지운 조각은 커밋된 채 남고, 다음 실행이 남은 사진부터 이어서 끝낸다")
    void resumesFromCommittedChunks() {
        // given
        Long albumId = givenAlbumWithData(LocalDateTime.now().minusDays(1));
        albumPurgeService.purgeChunk(albumId, 500);
        assertThat(count("photos", albumId)).isEqualTo(PHOTO_COUNT + 1 - 500);

        // when
        scheduler.purge();

        // then
        assertThat(count("photos", albumId)).isZero();
        assertThat(count("albums", albumId)).isZero();
    }

    @Test
    @DisplayName("마지막 단계에서 리스너가 실패하면 앨범 행·스토리지 정리 행이 함께 롤백되고, 다음 실행에서 끝낸다")
    void rollsBackFinalStepTogether() {
        // given
        Long albumId = saveAlbum(LocalDateTime.now().minusDays(1)).getId();
        FailingListenerConfig.failForAlbumId = albumId;

        // when
        scheduler.purge();

        // then
        assertThat(count("albums", albumId)).isOne();
        assertThat(count("album_storage_deletions", albumId)).isZero();

        // when
        FailingListenerConfig.failForAlbumId = null;
        scheduler.purge();

        // then
        assertThat(count("albums", albumId)).isZero();
        assertThat(count("album_storage_deletions", albumId)).isOne();
    }

    @Test
    @DisplayName("이미 지운 앨범에 다시 돌아도 오류 없이 그대로다")
    void secondRunIsNoop() {
        // given
        Long albumId = saveAlbum(LocalDateTime.now().minusDays(1)).getId();
        scheduler.purge();

        // when
        scheduler.purge();
        albumPurgeService.purgeChunk(albumId, 500);

        // then
        assertThat(count("albums", albumId)).isZero();
        assertThat(count("album_storage_deletions", albumId)).isOne();
    }

    // 사진 PHOTO_COUNT 장(짝수 번째만 업로드 완료, 나머지 PENDING) + 셀피 1장, 앞 10장에 얼굴·분석, 멤버 2명, 만료 알림 1건.
    private Long givenAlbumWithData(LocalDateTime expiresAt) {
        Album album = saveAlbum(expiresAt);
        Long albumId = album.getId();
        albumMemberRepository.save(AlbumMember.create(album, 1L, AlbumRole.OWNER));
        albumMemberRepository.save(AlbumMember.create(album, 2L, AlbumRole.MEMBER));
        noticeRepository.save(
                AlbumExpiryNotice.create(albumId, 2L, LocalDateTime.now().minusDays(8)));

        List<Photo> photos = new ArrayList<>();
        for (int i = 0; i < PHOTO_COUNT; i++) {
            Photo photo = Photo.create(albumId, 1L, String.format("%064d", i), 1000L, "image/jpeg");
            if (i % 2 == 0) {
                photo.complete(1L, 1000L, LocalDateTime.now());
            }
            photos.add(photo);
        }
        photos.add(Photo.createSelfie(albumId, 2L, String.format("%064d", PHOTO_COUNT), 1000L, "image/jpeg", true));
        List<Photo> saved = photoRepository.saveAll(photos);

        Long person = insertPerson(jdbcTemplate, albumId);
        for (int i = 0; i < 10; i++) {
            Long faceId = insertFace(jdbcTemplate, albumId, saved.get(i).getId(), person, 0.9);
            if (i == 0) {
                setCover(jdbcTemplate, person, faceId);
            }
            insertAnalysis(jdbcTemplate, albumId, saved.get(i).getId());
        }
        return albumId;
    }

    private Album saveAlbum(LocalDateTime expiresAt) {
        return albumRepository.save(Album.create(
                1L,
                "제주 여행",
                expiresAt.minusDays(Album.RETENTION_DAYS),
                UUID.randomUUID().toString().substring(0, 22)));
    }

    private Long count(String table, Long albumId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE album_id = ?", Long.class, albumId);
    }

    // 마지막 단계의 원자성을 보려고, 지정한 앨범에서만 실패하는 리스너를 더한다.
    @TestConfiguration
    static class FailingListenerConfig {
        static volatile Long failForAlbumId;

        @Bean
        FailingListener failingListener() {
            return new FailingListener();
        }
    }

    static class FailingListener {
        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void handle(AlbumPhotosPurgedEvent event) {
            if (event.albumId().equals(FailingListenerConfig.failForAlbumId)) {
                throw new IllegalStateException("의도한 실패");
            }
        }
    }
}
