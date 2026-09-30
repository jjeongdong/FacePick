package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumExpiryNotice;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumRole;
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
@Import({
    JpaAuditingConfig.class,
    AlbumRepositoryImpl.class,
    AlbumMemberRepositoryImpl.class,
    AlbumExpiryNoticeRepositoryImpl.class
})
@Testcontainers(disabledWithoutDocker = true)
class AlbumRepositoryImplQueryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlbumRepositoryImpl albumRepository;

    @Autowired
    private AlbumMemberRepositoryImpl albumMemberRepository;

    @Autowired
    private AlbumExpiryNoticeRepositoryImpl noticeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int inviteSeq;

    // 앨범은 생성 30일 뒤 만료되므로, 만료 시각으로 생성 시각을 역산한다.
    private Album albumExpiringAt(LocalDateTime expiresAt) {
        return albumRepository.save(
                Album.create(1L, "제주 여행", expiresAt.minusDays(Album.RETENTION_DAYS), "purge-code-" + inviteSeq++));
    }

    @Test
    @DisplayName("만료 앨범 조회 - 만료 시각이 지난(같은 시각 포함) 앨범만 오래된 순으로 limit 개")
    void findsExpiredIdsOldestFirst() {
        // given
        Album newest = albumExpiringAt(NOW);
        Album oldest = albumExpiringAt(NOW.minusDays(3));
        Album middle = albumExpiringAt(NOW.minusDays(1));
        albumExpiringAt(NOW.plusSeconds(1));

        // when
        List<Long> all = albumRepository.findExpiredIds(NOW, 10);
        List<Long> limited = albumRepository.findExpiredIds(NOW, 2);

        // then
        assertThat(all).containsExactly(oldest.getId(), middle.getId(), newest.getId());
        assertThat(limited).containsExactly(oldest.getId(), middle.getId());
    }

    @Test
    @DisplayName("만료 앨범 삭제 - 앨범 행을 지우고 멤버·알림 행은 FK CASCADE 로 함께 사라진다")
    void deletesExpiredAlbumWithCascade() {
        // given
        Album album = albumExpiringAt(NOW.minusDays(1));
        albumMemberRepository.save(AlbumMember.create(album, 1L, AlbumRole.OWNER));
        albumMemberRepository.save(AlbumMember.create(album, 2L, AlbumRole.MEMBER));
        noticeRepository.save(AlbumExpiryNotice.create(album.getId(), 2L, NOW.minusDays(8)));

        // when
        int deleted = albumRepository.deleteExpired(album.getId(), NOW);

        // then
        assertThat(deleted).isOne();
        assertThat(count("albums", album.getId())).isZero();
        assertThat(count("album_members", album.getId())).isZero();
        assertThat(count("album_expiry_notices", album.getId())).isZero();
    }

    @Test
    @DisplayName("만료 앨범 삭제 - 만료되지 않은 앨범은 지우지 않고 0 을 준다")
    void keepsAlbumNotYetExpired() {
        // given
        Album album = albumExpiringAt(NOW.plusSeconds(1));

        // when
        int deleted = albumRepository.deleteExpired(album.getId(), NOW);

        // then
        assertThat(deleted).isZero();
        assertThat(count("albums", album.getId())).isOne();
    }

    @Test
    @DisplayName("만료 앨범 삭제 - 이미 없는 앨범이면 0 을 준다")
    void deleteExpiredReturnsZeroForMissingAlbum() {
        // when & then
        assertThat(albumRepository.deleteExpired(999_999L, NOW)).isZero();
    }

    private Long count(String table, Long albumId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE album_id = ?", Long.class, albumId);
    }
}
