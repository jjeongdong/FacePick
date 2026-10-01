package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.auth.domain.EmailVerification;
import com.back.facepick.auth.domain.exception.AuthVerificationResendTooSoonException;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import java.time.LocalDateTime;
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
@Import({JpaAuditingConfig.class, EmailVerificationRepositoryImpl.class})
@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationRepositoryImplQueryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private EmailVerificationRepositoryImpl repository;

    @Test
    @DisplayName("이메일로 잠가서 찾는다")
    void findsByEmailForUpdate() {
        // given
        repository.save(EmailVerification.create("me@example.com", "123456", NOW));

        // when & then
        assertThat(repository.findByEmailForUpdate("me@example.com"))
                .get()
                .extracting(EmailVerification::getCodeHash)
                .isEqualTo(EmailVerification.hash("123456"));
        assertThat(repository.findByEmailForUpdate("other@example.com")).isEmpty();
    }

    @Test
    @DisplayName("같은 이메일 행을 또 만들면 유니크 제약이 재발송 간격 예외로 바뀐다 - 동시 첫 요청 중 하나만 통과")
    void duplicateIsTooSoon() {
        // given
        repository.save(EmailVerification.create("me@example.com", "123456", NOW));

        // when & then
        assertThatThrownBy(() -> repository.save(EmailVerification.create("me@example.com", "654321", NOW)))
                .isInstanceOf(AuthVerificationResendTooSoonException.class);
    }

    @Test
    @DisplayName("코드 해시가 같을 때만 지운다 - 그 사이 새로 발급된 코드는 남긴다")
    void deletesOnlyMatchingHash() {
        // given
        repository.save(EmailVerification.create("me@example.com", "123456", NOW));

        // when
        int other = repository.deleteByEmailAndCodeHash("me@example.com", EmailVerification.hash("000000"));
        int matching = repository.deleteByEmailAndCodeHash("me@example.com", EmailVerification.hash("123456"));

        // then
        assertThat(other).isZero();
        assertThat(matching).isEqualTo(1);
        assertThat(repository.findByEmailForUpdate("me@example.com")).isEmpty();
    }
}
