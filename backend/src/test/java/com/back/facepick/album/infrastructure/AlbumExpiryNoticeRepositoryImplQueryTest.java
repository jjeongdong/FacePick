package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumExpiryNotice;
import com.back.facepick.album.domain.AlbumExpiryNoticeStatus;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.time.Duration;
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
@Import({
    JpaAuditingConfig.class,
    AlbumExpiryNoticeRepositoryImpl.class,
    AlbumRepositoryImpl.class,
    AlbumMemberRepositoryImpl.class
})
@Testcontainers(disabledWithoutDocker = true)
class AlbumExpiryNoticeRepositoryImplQueryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final LocalDateTime UNTIL = NOW.plusDays(7);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlbumExpiryNoticeRepositoryImpl noticeRepository;

    @Autowired
    private AlbumExpiryNoticeJpaRepository noticeJpaRepository;

    @Autowired
    private AlbumRepositoryImpl albumRepository;

    @Autowired
    private AlbumMemberRepositoryImpl albumMemberRepository;

    private int inviteSeq;

    // 앨범은 생성 30일 뒤 만료되므로, 만료까지 남은 일수로 생성 시각을 역산한다.
    private Album albumExpiringIn(Duration untilExpiry, Long... memberIds) {
        LocalDateTime createdAt = NOW.plus(untilExpiry).minusDays(Album.RETENTION_DAYS);
        Album album = albumRepository.save(Album.create(memberIds[0], "제주 여행", createdAt, "code-" + inviteSeq++));
        albumMemberRepository.save(AlbumMember.create(album, memberIds[0], AlbumRole.OWNER));
        for (int i = 1; i < memberIds.length; i++) {
            albumMemberRepository.save(AlbumMember.create(album, memberIds[i], AlbumRole.MEMBER));
        }
        return album;
    }

    @Test
    @DisplayName("행 생성 - 7일 안에 만료되는 앨범의 멤버마다 PENDING 행을 만든다")
    void createsForMembersOfExpiringAlbum() {
        // given
        Album album = albumExpiringIn(Duration.ofDays(5), 1L, 2L);

        // when
        int created = noticeRepository.createPendingForAlbumsExpiringBetween(NOW, UNTIL);

        // then
        assertThat(created).isEqualTo(2);
        assertThat(noticeJpaRepository.findAll())
                .extracting(
                        AlbumExpiryNotice::getAlbumId,
                        AlbumExpiryNotice::getUserId,
                        AlbumExpiryNotice::getStatus,
                        AlbumExpiryNotice::getAttempts,
                        AlbumExpiryNotice::getNextAttemptAt)
                .containsExactlyInAnyOrder(
                        tuple(album.getId(), 1L, AlbumExpiryNoticeStatus.PENDING, 0, NOW),
                        tuple(album.getId(), 2L, AlbumExpiryNoticeStatus.PENDING, 0, NOW));
    }

    @Test
    @DisplayName("행 생성 - 멱등 키를 DB 가 행마다 다른 값으로 채운다")
    void fillsDistinctIdempotencyKeys() {
        // given
        albumExpiringIn(Duration.ofDays(5), 1L, 2L);

        // when
        noticeRepository.createPendingForAlbumsExpiringBetween(NOW, UNTIL);

        // then
        List<String> keys = noticeJpaRepository.findAll().stream()
                .map(AlbumExpiryNotice::idempotencyKey)
                .toList();
        assertThat(keys).hasSize(2).doesNotHaveDuplicates().noneMatch(key -> key.endsWith("null"));
    }

    @Test
    @DisplayName("행 생성 - 정확히 7일 뒤 만료는 포함, 7일 넘게 남았거나 이미 만료된 앨범은 뺀다")
    void appliesWindowBoundaries() {
        // given
        Album exactlySevenDays = albumExpiringIn(Duration.ofDays(7), 1L);
        albumExpiringIn(Duration.ofDays(7).plusSeconds(1), 2L);
        albumExpiringIn(Duration.ZERO, 3L);
        albumExpiringIn(Duration.ofDays(-1), 4L);

        // when
        noticeRepository.createPendingForAlbumsExpiringBetween(NOW, UNTIL);

        // then
        assertThat(noticeJpaRepository.findAll())
                .extracting(AlbumExpiryNotice::getAlbumId, AlbumExpiryNotice::getUserId)
                .containsExactly(tuple(exactlySevenDays.getId(), 1L));
    }

    @Test
    @DisplayName("행 생성 - 두 번 돌려도 멤버마다 한 행이고, 그 사이 들어온 멤버만 추가된다")
    void isIdempotentAndPicksUpNewMembers() {
        // given
        Album album = albumExpiringIn(Duration.ofDays(5), 1L);
        noticeRepository.createPendingForAlbumsExpiringBetween(NOW, UNTIL);
        albumMemberRepository.save(AlbumMember.create(album, 2L, AlbumRole.MEMBER));

        // when
        int created = noticeRepository.createPendingForAlbumsExpiringBetween(NOW.plusHours(1), UNTIL.plusHours(1));

        // then
        assertThat(created).isEqualTo(1);
        assertThat(noticeJpaRepository.findAll())
                .extracting(AlbumExpiryNotice::getUserId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("선점 조회 - PENDING 이고 시도 시각이 된 행만 오래된 순으로 limit 개")
    void findsDueRowsInOrder() {
        // given
        Album album = albumExpiringIn(Duration.ofDays(5), 1L, 2L, 3L, 4L, 5L);
        AlbumExpiryNotice later =
                noticeRepository.save(AlbumExpiryNotice.create(album.getId(), 1L, NOW.minusMinutes(1)));
        AlbumExpiryNotice earlier =
                noticeRepository.save(AlbumExpiryNotice.create(album.getId(), 2L, NOW.minusMinutes(2)));
        noticeRepository.save(AlbumExpiryNotice.create(album.getId(), 3L, NOW.plusSeconds(1)));
        AlbumExpiryNotice sent = AlbumExpiryNotice.create(album.getId(), 4L, NOW.minusMinutes(3));
        sent.markSent("re_1", NOW);
        noticeRepository.save(sent);
        AlbumExpiryNotice third = noticeRepository.save(AlbumExpiryNotice.create(album.getId(), 5L, NOW));

        // when
        List<AlbumExpiryNotice> due = noticeRepository.findDueForUpdate(NOW, 2);

        // then
        assertThat(due).extracting(AlbumExpiryNotice::getId).containsExactly(earlier.getId(), later.getId());
        assertThat(noticeRepository.findDueForUpdate(NOW, 10))
                .extracting(AlbumExpiryNotice::getId)
                .containsExactly(earlier.getId(), later.getId(), third.getId());
    }

    @Test
    @DisplayName("선점 조회 - 임대 중인 행은 임대가 끝나기 전엔 안 잡히고, 끝나면 다시 잡힌다")
    void leasedRowReturnsAfterLeaseExpires() {
        // given
        Album album = albumExpiringIn(Duration.ofDays(5), 1L);
        AlbumExpiryNotice notice = AlbumExpiryNotice.create(album.getId(), 1L, NOW);
        notice.claim(NOW, Duration.ofMinutes(5));
        noticeRepository.save(notice);

        // when & then
        assertThat(noticeRepository.findDueForUpdate(NOW.plusMinutes(4), 10)).isEmpty();
        assertThat(noticeRepository.findDueForUpdate(NOW.plusMinutes(5), 10))
                .extracting(AlbumExpiryNotice::getId)
                .containsExactly(notice.getId());
    }

    @Test
    @DisplayName("앨범 여러 개 조회 - 없는 ID 는 빠진다")
    void findAllAlbumsByIds() {
        // given
        Album album = albumExpiringIn(Duration.ofDays(5), 1L);

        // when & then
        assertThat(albumRepository.findAllByIds(List.of(album.getId(), 999_999L)))
                .extracting(Album::getId)
                .containsExactly(album.getId());
    }
}
