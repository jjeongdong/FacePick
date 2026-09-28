package com.back.facepick.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.auth.domain.RefreshTokenRepository;
import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidRefreshTokenException;
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

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 12, 0);
    private static final String ACCESS_TOKEN = "Bearer access";
    private static final String REFRESH_TOKEN = "refresh";

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

    @InjectMocks
    private AuthCommandService authCommandService;

    private void givenTokens(Long userId) {
        given(tokenProvider.createAccessToken(userId, "ROLE_USER")).willReturn(ACCESS_TOKEN);
        given(tokenProvider.createRefreshToken(userId)).willReturn(REFRESH_TOKEN);
        given(tokenProvider.refreshTokenTtlMillis()).willReturn(60_000L);
    }

    @Test
    @DisplayName("회원가입하면 사용자·자격 증명을 만들고 토큰을 발급하며 refresh 토큰은 해시로 저장한다")
    void signUp() {
        // given
        given(userCommandService.createUser(new UserCreateCommand("민수")))
                .willReturn(new UserCreateResult(1L, "ROLE_USER", CREATED_AT));
        givenTokens(1L);

        // when
        SignUpResult result = authCommandService.signUp(new SignUpCommand("me@example.com", "password123", "민수"));

        // then
        assertThat(result).isEqualTo(new SignUpResult(1L, ACCESS_TOKEN, REFRESH_TOKEN, CREATED_AT));
        then(credentialRepository).should().save(any(Credential.class));
        then(refreshTokenRepository).should().save(argThat(token -> token.getTokenHash()
                .equals(RefreshToken.hash(REFRESH_TOKEN))));
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
