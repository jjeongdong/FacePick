package com.back.facepick.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.back.facepick.auth.application.dto.command.EmailVerificationSendCommand;
import com.back.facepick.auth.application.dto.command.LoginCommand;
import com.back.facepick.auth.application.dto.command.LogoutCommand;
import com.back.facepick.auth.application.dto.command.SignUpCommand;
import com.back.facepick.auth.application.dto.command.TokenReissueCommand;
import com.back.facepick.auth.application.dto.result.LoginResult;
import com.back.facepick.auth.application.dto.result.SignUpResult;
import com.back.facepick.auth.application.dto.result.TokenReissueResult;
import com.back.facepick.auth.domain.AuthTokenProvider;
import com.back.facepick.auth.domain.Credential;
import com.back.facepick.auth.domain.CredentialRepository;
import com.back.facepick.auth.domain.EmailVerification;
import com.back.facepick.auth.domain.EmailVerificationRepository;
import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.auth.domain.RefreshTokenRepository;
import com.back.facepick.auth.domain.VerificationCodeGenerator;
import com.back.facepick.auth.domain.VerificationMailSender;
import com.back.facepick.auth.domain.exception.AuthEmailAlreadyExistsException;
import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidRefreshTokenException;
import com.back.facepick.auth.domain.exception.AuthVerificationCodeExpiredException;
import com.back.facepick.auth.domain.exception.AuthVerificationCodeMismatchException;
import com.back.facepick.auth.domain.exception.AuthVerificationMailUnavailableException;
import com.back.facepick.auth.domain.exception.AuthVerificationResendTooSoonException;
import com.back.facepick.auth.fixture.CredentialFixture;
import com.back.facepick.auth.fixture.FakePasswordEncryptor;
import com.back.facepick.user.application.UserCommandService;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.application.dto.api.UserInfo;
import com.back.facepick.user.application.dto.command.UserCreateCommand;
import com.back.facepick.user.application.dto.result.UserCreateResult;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final String ACCESS_TOKEN = "Bearer access";
    private static final String REFRESH_TOKEN = "refresh";
    private static final String CODE = "012345";
    private static final LocalDateTime LONG_AGO = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Spy
    private FakePasswordEncryptor passwordEncryptor = new FakePasswordEncryptor();

    @Mock
    private AuthTokenProvider tokenProvider;

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private UserQueryApi userQueryApi;

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private VerificationCodeGenerator verificationCodeGenerator;

    @Mock
    private VerificationMailSender verificationMailSender;

    // 트랜잭션 경계는 AuthEmailVerificationIntegrationTest 가 실제 DB 로 본다. 여기서는 흐름만.
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private AuthCommandService authCommandService;

    private void givenTokens(Long userId) {
        given(tokenProvider.createAccessToken(userId, "ROLE_USER")).willReturn(ACCESS_TOKEN);
        given(tokenProvider.createRefreshToken(userId)).willReturn(REFRESH_TOKEN);
        given(tokenProvider.refreshTokenTtlMillis()).willReturn(60_000L);
    }

    @Nested
    @DisplayName("인증 코드 발송")
    class SendEmailVerification {

        @Test
        @DisplayName("처음이면 코드 행을 저장한 뒤 메일을 보낸다")
        void savesThenSends() {
            // given
            given(verificationCodeGenerator.generate()).willReturn(CODE);
            given(emailVerificationRepository.findByEmailForUpdate("me@example.com"))
                    .willReturn(Optional.empty());

            // when
            authCommandService.sendEmailVerification(new EmailVerificationSendCommand("Me@example.com"));

            // then
            then(emailVerificationRepository)
                    .should()
                    .save(argThat(v -> v.getEmail().equals("me@example.com")
                            && v.getCodeHash().equals(EmailVerification.hash(CODE))));
            then(verificationMailSender).should().send("me@example.com", CODE);
        }

        @Test
        @DisplayName("이미 행이 있으면 새 코드로 재발급한다")
        void reissuesExisting() {
            // given
            EmailVerification existing = EmailVerification.create("me@example.com", "111111", LONG_AGO);
            given(verificationCodeGenerator.generate()).willReturn(CODE);
            given(emailVerificationRepository.findByEmailForUpdate("me@example.com"))
                    .willReturn(Optional.of(existing));

            // when
            authCommandService.sendEmailVerification(new EmailVerificationSendCommand("me@example.com"));

            // then
            assertThat(existing.getCodeHash()).isEqualTo(EmailVerification.hash(CODE));
            then(emailVerificationRepository).should(never()).save(any());
            then(verificationMailSender).should().send("me@example.com", CODE);
        }

        @Test
        @DisplayName("60초 안이면 메일을 보내지 않고 AuthVerificationResendTooSoonException")
        void tooSoon() {
            // given
            given(verificationCodeGenerator.generate()).willReturn(CODE);
            given(emailVerificationRepository.findByEmailForUpdate("me@example.com"))
                    .willReturn(Optional.of(EmailVerification.create("me@example.com", "111111", LocalDateTime.now())));

            // when & then
            assertThatThrownBy(() -> authCommandService.sendEmailVerification(
                            new EmailVerificationSendCommand("me@example.com")))
                    .isInstanceOf(AuthVerificationResendTooSoonException.class);
            then(verificationMailSender).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 메일을 보내지 않고 AuthEmailAlreadyExistsException")
        void alreadyRegistered() {
            // given
            given(verificationCodeGenerator.generate()).willReturn(CODE);
            given(credentialRepository.findByEmail("me@example.com"))
                    .willReturn(Optional.of(CredentialFixture.credential(1L, "me@example.com")));

            // when & then
            assertThatThrownBy(() -> authCommandService.sendEmailVerification(
                            new EmailVerificationSendCommand("me@example.com")))
                    .isInstanceOf(AuthEmailAlreadyExistsException.class);
            then(verificationMailSender).shouldHaveNoInteractions();
            then(emailVerificationRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("메일 발송에 실패하면 방금 만든 코드만 지우고 예외를 그대로 던진다")
        void releasesCodeOnMailFailure() {
            // given
            given(verificationCodeGenerator.generate()).willReturn(CODE);
            given(emailVerificationRepository.findByEmailForUpdate("me@example.com"))
                    .willReturn(Optional.empty());
            willThrow(new AuthVerificationMailUnavailableException())
                    .given(verificationMailSender)
                    .send("me@example.com", CODE);

            // when & then
            assertThatThrownBy(() -> authCommandService.sendEmailVerification(
                            new EmailVerificationSendCommand("me@example.com")))
                    .isInstanceOf(AuthVerificationMailUnavailableException.class);
            then(emailVerificationRepository)
                    .should()
                    .deleteByEmailAndCodeHash("me@example.com", EmailVerification.hash(CODE));
        }

        @Test
        @DisplayName("코드 반납이 실패해도 원래 메일 예외를 던진다")
        void keepsMailExceptionWhenReleaseFails() {
            // given
            given(verificationCodeGenerator.generate()).willReturn(CODE);
            given(emailVerificationRepository.findByEmailForUpdate("me@example.com"))
                    .willReturn(Optional.empty());
            willThrow(new AuthVerificationMailUnavailableException())
                    .given(verificationMailSender)
                    .send("me@example.com", CODE);
            given(emailVerificationRepository.deleteByEmailAndCodeHash("me@example.com", EmailVerification.hash(CODE)))
                    .willThrow(new IllegalStateException("db down"));

            // when & then
            assertThatThrownBy(() -> authCommandService.sendEmailVerification(
                            new EmailVerificationSendCommand("me@example.com")))
                    .isInstanceOf(AuthVerificationMailUnavailableException.class)
                    .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
        }
    }

    @Nested
    @DisplayName("회원가입")
    class SignUp {

        private final SignUpCommand command = new SignUpCommand("Me@example.com", "password123", "민수", CODE);

        @Test
        @DisplayName("코드가 맞으면 코드를 소비하고 사용자·자격 증명을 만들어 토큰을 발급하며 refresh 토큰은 해시로 저장한다")
        void signUp() {
            // given
            given(emailVerificationRepository.findByEmailForUpdate("me@example.com"))
                    .willReturn(Optional.of(EmailVerification.create("me@example.com", CODE, LocalDateTime.now())));
            given(emailVerificationRepository.deleteByEmailAndCodeHash("me@example.com", EmailVerification.hash(CODE)))
                    .willReturn(1);
            given(userCommandService.createUser(new UserCreateCommand("민수")))
                    .willReturn(new UserCreateResult(1L, "ROLE_USER", CREATED_AT));
            givenTokens(1L);

            // when
            SignUpResult result = authCommandService.signUp(command);

            // then
            assertThat(result).isEqualTo(new SignUpResult(1L, ACCESS_TOKEN, REFRESH_TOKEN, CREATED_AT));
            then(credentialRepository).should().save(any(Credential.class));
            then(refreshTokenRepository).should().save(argThat(token -> token.getTokenHash()
                    .equals(RefreshToken.hash(REFRESH_TOKEN))));
        }

        @Test
        @DisplayName("코드를 받은 적이 없으면 AuthVerificationCodeExpiredException")
        void noCode() {
            // given
            given(emailVerificationRepository.findByEmailForUpdate("me@example.com"))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authCommandService.signUp(command))
                    .isInstanceOf(AuthVerificationCodeExpiredException.class);
            then(userCommandService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("코드가 틀리면 틀린 횟수를 올리고 가입하지 않고 AuthVerificationCodeMismatchException")
        void mismatch() {
            // given
            EmailVerification verification = EmailVerification.create("me@example.com", "999999", LocalDateTime.now());
            given(emailVerificationRepository.findByEmailForUpdate("me@example.com"))
                    .willReturn(Optional.of(verification));

            // when & then
            assertThatThrownBy(() -> authCommandService.signUp(command))
                    .isInstanceOf(AuthVerificationCodeMismatchException.class);
            assertThat(verification.getAttempts()).isEqualTo(1);
            then(userCommandService).shouldHaveNoInteractions();
            then(emailVerificationRepository).should(never()).deleteByEmailAndCodeHash(anyString(), anyString());
        }

        @Test
        @DisplayName("확인과 가입 사이에 코드가 쓰였거나 바뀌면(삭제 0행) AuthVerificationCodeExpiredException")
        void codeConsumedConcurrently() {
            // given
            given(emailVerificationRepository.findByEmailForUpdate("me@example.com"))
                    .willReturn(Optional.of(EmailVerification.create("me@example.com", CODE, LocalDateTime.now())));
            given(emailVerificationRepository.deleteByEmailAndCodeHash("me@example.com", EmailVerification.hash(CODE)))
                    .willReturn(0);

            // when & then
            assertThatThrownBy(() -> authCommandService.signUp(command))
                    .isInstanceOf(AuthVerificationCodeExpiredException.class);
            then(userCommandService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("이메일 대소문자와 상관없이 로그인하고 토큰을 발급한다")
        void issuesTokens() {
            // given
            given(credentialRepository.findByEmail("me@example.com"))
                    .willReturn(Optional.of(CredentialFixture.credential(1L, "me@example.com")));
            given(userQueryApi.getInfo(1L)).willReturn(new UserInfo(1L, "민수", "ROLE_USER"));
            givenTokens(1L);

            // when
            LoginResult result =
                    authCommandService.login(new LoginCommand("ME@example.com", CredentialFixture.PASSWORD));

            // then
            assertThat(result).isEqualTo(new LoginResult(1L, ACCESS_TOKEN, REFRESH_TOKEN));
        }

        @Test
        @DisplayName("가입하지 않은 이메일이면 AuthInvalidCredentialsException 을 던진다")
        void throwsWhenEmailUnknown() {
            // given
            given(credentialRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authCommandService.login(new LoginCommand("nobody@example.com", "password123")))
                    .isInstanceOf(AuthInvalidCredentialsException.class);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 토큰을 발급하지 않고 AuthInvalidCredentialsException 을 던진다")
        void throwsWhenPasswordWrong() {
            // given
            given(credentialRepository.findByEmail("me@example.com"))
                    .willReturn(Optional.of(CredentialFixture.credential(1L, "me@example.com")));

            // when & then
            assertThatThrownBy(() -> authCommandService.login(new LoginCommand("me@example.com", "wrong-password")))
                    .isInstanceOf(AuthInvalidCredentialsException.class);
            then(tokenProvider).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("토큰 재발급")
    class Reissue {

        @Test
        @DisplayName("저장된 refresh 토큰이면 최신 권한으로 access 토큰을 다시 발급한다")
        void reissuesAccessToken() {
            // given
            given(tokenProvider.getUserIdFromRefreshToken(REFRESH_TOKEN)).willReturn(1L);
            given(refreshTokenRepository.existsByTokenHash(RefreshToken.hash(REFRESH_TOKEN)))
                    .willReturn(true);
            given(userQueryApi.getInfo(1L)).willReturn(new UserInfo(1L, "민수", "ROLE_USER"));
            given(tokenProvider.createAccessToken(1L, "ROLE_USER")).willReturn(ACCESS_TOKEN);

            // when
            TokenReissueResult result = authCommandService.reissueToken(new TokenReissueCommand(REFRESH_TOKEN));

            // then
            assertThat(result).isEqualTo(new TokenReissueResult(ACCESS_TOKEN));
        }

        @Test
        @DisplayName("로그아웃으로 지운 refresh 토큰이면 AuthInvalidRefreshTokenException 을 던진다")
        void throwsWhenTokenRemoved() {
            // given
            given(tokenProvider.getUserIdFromRefreshToken(REFRESH_TOKEN)).willReturn(1L);
            given(refreshTokenRepository.existsByTokenHash(RefreshToken.hash(REFRESH_TOKEN)))
                    .willReturn(false);

            // when & then
            assertThatThrownBy(() -> authCommandService.reissueToken(new TokenReissueCommand(REFRESH_TOKEN)))
                    .isInstanceOf(AuthInvalidRefreshTokenException.class);
        }
    }

    @Test
    @DisplayName("로그아웃하면 refresh 토큰을 해시로 찾아 지운다")
    void logout() {
        // when
        authCommandService.logout(new LogoutCommand(REFRESH_TOKEN));

        // then
        then(refreshTokenRepository).should().deleteByTokenHash(RefreshToken.hash(REFRESH_TOKEN));
    }
}
