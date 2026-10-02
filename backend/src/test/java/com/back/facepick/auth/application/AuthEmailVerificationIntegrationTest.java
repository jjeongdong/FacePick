package com.back.facepick.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.auth.application.dto.command.EmailVerificationSendCommand;
import com.back.facepick.auth.application.dto.command.SignUpCommand;
import com.back.facepick.auth.domain.AuthTokenProvider;
import com.back.facepick.auth.domain.EmailVerification;
import com.back.facepick.auth.domain.VerificationCodeGenerator;
import com.back.facepick.auth.domain.VerificationMailSender;
import com.back.facepick.auth.domain.exception.AuthInvalidPasswordException;
import com.back.facepick.auth.domain.exception.AuthVerificationCodeMismatchException;
import com.back.facepick.auth.domain.exception.AuthVerificationMailUnavailableException;
import com.back.facepick.auth.fixture.FakePasswordEncryptor;
import com.back.facepick.auth.infrastructure.CredentialJpaRepository;
import com.back.facepick.auth.infrastructure.CredentialRepositoryImpl;
import com.back.facepick.auth.infrastructure.EmailVerificationJpaRepository;
import com.back.facepick.auth.infrastructure.EmailVerificationRepositoryImpl;
import com.back.facepick.auth.infrastructure.RefreshTokenRepositoryImpl;
import com.back.facepick.global.config.data.JpaAuditingConfig;
import com.back.facepick.user.application.UserCommandService;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.infrastructure.UserRepositoryImpl;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 단위 테스트(AuthCommandServiceTest)는 가짜 트랜잭션 위에서 흐름만 본다.
// 여기서는 실제 트랜잭션으로 틀린 횟수가 가입 실패 뒤에도 남는지, 발송 실패 반납이 새 코드를 지우지 않는지 본다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
    JpaAuditingConfig.class,
    CredentialRepositoryImpl.class,
    RefreshTokenRepositoryImpl.class,
    EmailVerificationRepositoryImpl.class,
    UserRepositoryImpl.class,
    UserCommandService.class,
    UserQueryApi.class,
    AuthCommandService.class,
    AuthEmailVerificationIntegrationTest.FakeConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class AuthEmailVerificationIntegrationTest {

    private static final String EMAIL = "me@example.com";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private AuthCommandService authCommandService;

    @Autowired
    private EmailVerificationJpaRepository emailVerificationJpaRepository;

    @Autowired
    private CredentialJpaRepository credentialJpaRepository;

    @Autowired
    private FakeCodeGenerator codeGenerator;

    @Autowired
    private FakeMailSender mailSender;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        emailVerificationJpaRepository.deleteAll();
        credentialJpaRepository.deleteAll();
        mailSender.onSend = null;
    }

    private EmailVerification stored() {
        return emailVerificationJpaRepository.findAll().get(0);
    }

    @Test
    @DisplayName("틀린 코드로 가입하면 가입은 안 되지만 틀린 횟수는 DB 에 남는다")
    void mismatchCountIsCommitted() {
        // given
        codeGenerator.codes.push("123456");
        authCommandService.sendEmailVerification(new EmailVerificationSendCommand(EMAIL));

        // when & then
        assertThatThrownBy(() -> authCommandService.signUp(new SignUpCommand(EMAIL, "password123", "민수", "999999")))
                .isInstanceOf(AuthVerificationCodeMismatchException.class);
        assertThat(stored().getAttempts()).isEqualTo(1);
        assertThat(credentialJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("맞는 코드로 가입하면 계정이 생기고 코드는 지워져 다시 쓸 수 없다")
    void signUpConsumesCode() {
        // given
        codeGenerator.codes.push("123456");
        authCommandService.sendEmailVerification(new EmailVerificationSendCommand(EMAIL));

        // when
        authCommandService.signUp(new SignUpCommand(EMAIL, "password123", "민수", "123456"));

        // then
        assertThat(credentialJpaRepository.findByEmail(EMAIL)).isPresent();
        assertThat(emailVerificationJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("비밀번호 규칙에 걸려 가입이 롤백되면 코드도 지워지지 않아 다시 가입할 수 있다")
    void codeSurvivesRolledBackSignUp() {
        // given
        codeGenerator.codes.push("123456");
        authCommandService.sendEmailVerification(new EmailVerificationSendCommand(EMAIL));

        // when
        assertThatThrownBy(() -> authCommandService.signUp(new SignUpCommand(EMAIL, "x".repeat(80), "민수", "123456")))
                .isInstanceOf(AuthInvalidPasswordException.class);

        // then
        assertThat(emailVerificationJpaRepository.count()).isEqualTo(1);
        authCommandService.signUp(new SignUpCommand(EMAIL, "password123", "민수", "123456"));
        assertThat(credentialJpaRepository.findByEmail(EMAIL)).isPresent();
    }

    @Test
    @DisplayName("발송에 실패하면 코드를 지워 바로 다시 요청할 수 있다")
    void failedSendReleasesCode() {
        // given
        codeGenerator.codes.push("123456");
        mailSender.onSend = () -> {
            throw new AuthVerificationMailUnavailableException();
        };

        // when & then
        assertThatThrownBy(() -> authCommandService.sendEmailVerification(new EmailVerificationSendCommand(EMAIL)))
                .isInstanceOf(AuthVerificationMailUnavailableException.class);
        assertThat(emailVerificationJpaRepository.count()).isZero();
        mailSender.onSend = null;
        codeGenerator.codes.push("654321");
        authCommandService.sendEmailVerification(new EmailVerificationSendCommand(EMAIL));
        assertThat(stored().getCodeHash()).isEqualTo(EmailVerification.hash("654321"));
    }

    @Test
    @DisplayName("발송 실패 반납은 그 사이 다른 요청이 새로 발급한 코드를 지우지 않는다")
    void releaseKeepsNewerCode() {
        // given
        codeGenerator.codes.push("123456");
        mailSender.onSend = () -> {
            // 이 요청이 Resend 를 기다리는 동안 다른 요청이 새 코드를 발급하고 커밋했다고 본다.
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                EmailVerification row = emailVerificationJpaRepository
                        .findByEmailForUpdate(EMAIL)
                        .orElseThrow();
                row.reissue("777777", LocalDateTime.now().plusMinutes(1));
            });
            throw new AuthVerificationMailUnavailableException();
        };

        // when & then
        assertThatThrownBy(() -> authCommandService.sendEmailVerification(new EmailVerificationSendCommand(EMAIL)))
                .isInstanceOf(AuthVerificationMailUnavailableException.class);
        assertThat(stored().getCodeHash()).isEqualTo(EmailVerification.hash("777777"));
    }

    @TestConfiguration
    static class FakeConfig {
        @Bean
        FakeCodeGenerator fakeCodeGenerator() {
            return new FakeCodeGenerator();
        }

        @Bean
        FakeMailSender fakeMailSender() {
            return new FakeMailSender();
        }

        @Bean
        FakePasswordEncryptor fakePasswordEncryptor() {
            return new FakePasswordEncryptor();
        }

        @Bean
        AuthTokenProvider fakeTokenProvider() {
            return new AuthTokenProvider() {
                @Override
                public String createAccessToken(Long userId, String role) {
                    return "Bearer access-" + userId;
                }

                @Override
                public String createRefreshToken(Long userId) {
                    return "refresh-" + userId + "-" + System.nanoTime();
                }

                @Override
                public Long getUserIdFromRefreshToken(String refreshToken) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public long refreshTokenTtlMillis() {
                    return 60_000L;
                }
            };
        }
    }

    static class FakeCodeGenerator implements VerificationCodeGenerator {
        final Deque<String> codes = new ArrayDeque<>();

        @Override
        public String generate() {
            return codes.pop();
        }
    }

    static class FakeMailSender implements VerificationMailSender {
        Runnable onSend;

        @Override
        public void send(String email, String code) {
            if (onSend != null) {
                onSend.run();
            }
        }
    }
}
