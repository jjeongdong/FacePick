package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, RefreshTokenRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenRepositoryImplQueryTest {

    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private RefreshTokenRepositoryImpl refreshTokenRepository;

    @Test
    @DisplayName("저장한 토큰은 해시로 찾을 수 있고, 지우면 사라지며, 없는 토큰을 지워도 조용히 끝난다")
    void savesExistsAndDeletes() {
        // given
        refreshTokenRepository.save(RefreshToken.create(1L, "raw-token", EXPIRES_AT));
        String tokenHash = RefreshToken.hash("raw-token");

        // when & then
        assertThat(refreshTokenRepository.existsByTokenHash(tokenHash)).isTrue();
        refreshTokenRepository.deleteByTokenHash(tokenHash);
        refreshTokenRepository.deleteByTokenHash(tokenHash);
        assertThat(refreshTokenRepository.existsByTokenHash(tokenHash)).isFalse();
    }
}
