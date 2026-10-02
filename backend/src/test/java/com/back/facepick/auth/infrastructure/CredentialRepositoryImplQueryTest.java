package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.auth.domain.Credential;
import com.back.facepick.auth.domain.exception.AuthEmailAlreadyExistsException;
import com.back.facepick.auth.fixture.CredentialFixture;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.util.List;
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

// local 프로필의 개발 DB 대신 컨테이너 DB 에만 붙는다. 스키마는 Flyway 마이그레이션으로 만든다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, CredentialRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class CredentialRepositoryImplQueryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private CredentialRepositoryImpl credentialRepository;

    @Test
    @DisplayName("정규화된 이메일로 찾는다")
    void findsByNormalizedEmail() {
        // given
        credentialRepository.save(CredentialFixture.credential(1L, "Me@Example.com"));

        // when & then
        assertThat(credentialRepository.findByEmail("me@example.com"))
                .get()
                .extracting(Credential::getUserId)
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("대소문자만 다른 이메일을 다시 저장하면 유니크 제약이 AuthEmailAlreadyExistsException 으로 바뀐다")
    void duplicateEmailIsConflict() {
        // given
        credentialRepository.save(CredentialFixture.credential(1L, "me@example.com"));

        // when & then
        assertThatThrownBy(() -> credentialRepository.save(CredentialFixture.credential(2L, "ME@example.com")))
                .isInstanceOf(AuthEmailAlreadyExistsException.class);
    }

    @Test
    @DisplayName("사용자 ID 여러 개로 찾는다. 없는 ID 는 빠진다")
    void findsAllByUserIds() {
        // given
        credentialRepository.save(CredentialFixture.credential(1L, "a@example.com"));
        credentialRepository.save(CredentialFixture.credential(2L, "b@example.com"));

        // when & then
        assertThat(credentialRepository.findAllByUserIds(List.of(1L, 999L)))
                .extracting(Credential::getEmail)
                .containsExactly("a@example.com");
    }
}
