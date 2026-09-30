package com.back.facepick.album.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.album.application.AlbumCommandService;
import com.back.facepick.album.domain.Album;
import com.back.facepick.album.infrastructure.AlbumMemberRepositoryImpl;
import com.back.facepick.album.infrastructure.AlbumRepositoryImpl;
import com.back.facepick.album.infrastructure.SecureRandomInviteCodeGenerator;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.photo.domain.event.AlbumPhotosPurgedEvent;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// 커밋 시점 동작을 봐야 해서 테스트 트랜잭션을 쓰지 않는다. 행이 테스트 사이에 남으므로 테스트마다 새 앨범을 쓴다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
    JpaAuditingConfig.class,
    AlbumRepositoryImpl.class,
    AlbumMemberRepositoryImpl.class,
    SecureRandomInviteCodeGenerator.class,
    AlbumCommandService.class,
    AlbumPhotosPurgedListener.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AlbumPhotosPurgedListenerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AlbumCommandService albumCommandService;

    @Autowired
    private AlbumRepositoryImpl albumRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Album albumExpiringAt(LocalDateTime expiresAt) {
        return albumRepository.save(Album.create(
                1L,
                "제주 여행",
                expiresAt.minusDays(Album.RETENTION_DAYS),
                UUID.randomUUID().toString().substring(0, 22)));
    }

    @Test
    @DisplayName("사진 정리 트랜잭션에서 이벤트를 발행하면 커밋과 함께 만료 앨범 행이 지워진다")
    void deletesExpiredAlbumOnCommit() {
        // given
        Album album = albumExpiringAt(LocalDateTime.now().minusDays(1));

        // when
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> eventPublisher.publishEvent(new AlbumPhotosPurgedEvent(album.getId())));

        // then
        assertThat(countAlbum(album.getId())).isZero();
    }

    @Test
    @DisplayName("만료 전 앨범이면 이벤트가 와도 지우지 않는다")
    void keepsAlbumNotYetExpired() {
        // given
        Album album = albumExpiringAt(LocalDateTime.now().plusDays(1));

        // when
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> eventPublisher.publishEvent(new AlbumPhotosPurgedEvent(album.getId())));

        // then
        assertThat(countAlbum(album.getId())).isOne();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 부르면 IllegalTransactionStateException 이고 지우지 않는다")
    void requiresTransaction() {
        // given
        Album album = albumExpiringAt(LocalDateTime.now().minusDays(1));

        // when & then
        assertThatThrownBy(() -> albumCommandService.deleteExpiredAlbum(album.getId(), LocalDateTime.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(countAlbum(album.getId())).isOne();
    }

    private Long countAlbum(Long albumId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM albums WHERE album_id = ?", Long.class, albumId);
    }
}
