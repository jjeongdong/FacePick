package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.album.domain.Album;
import com.back.facepick.album.domain.AlbumMember;
import com.back.facepick.album.domain.AlbumRole;
import com.back.facepick.album.domain.exception.AlbumNotMemberException;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.Hibernate;
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
@Import({JpaAuditingConfig.class, AlbumMemberRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class AlbumMemberRepositoryImplQueryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AlbumMemberRepositoryImpl albumMemberRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("내 앨범 목록 - 내가 참여한 앨범만 최근 참여순으로, 앨범을 함께 불러온다")
    void findsMyAlbumsLatestFirstWithAlbum() {
        // given
        Album jeju = entityManager.persist(Album.create(1L, "제주", NOW));
        Album busan = entityManager.persist(Album.create(2L, "부산", NOW));
        albumMemberRepository.save(AlbumMember.create(jeju, 1L, AlbumRole.OWNER));
        albumMemberRepository.save(AlbumMember.create(busan, 2L, AlbumRole.OWNER));
        albumMemberRepository.save(AlbumMember.create(busan, 1L, AlbumRole.MEMBER));
        entityManager.flush();
        entityManager.clear();

        // when
        List<AlbumMember> members = albumMemberRepository.findAllByUserIdWithAlbum(1L);

        // then
        assertThat(members).extracting(member -> member.getAlbum().getTitle()).containsExactly("부산", "제주");
        assertThat(members).allSatisfy(member -> assertThat(Hibernate.isInitialized(member.getAlbum()))
                .isTrue());
    }

    @Test
    @DisplayName("참여자 조회 - 참여하지 않은 앨범이면 AlbumNotMemberException")
    void throwsWhenNotMember() {
        // given
        Album jeju = entityManager.persist(Album.create(1L, "제주", NOW));
        albumMemberRepository.save(AlbumMember.create(jeju, 1L, AlbumRole.OWNER));

        // when & then
        assertThatThrownBy(() -> albumMemberRepository.getByAlbumIdAndUserId(jeju.getId(), 2L))
                .isInstanceOf(AlbumNotMemberException.class);
    }
}
