package com.back.facepick.auth.application;

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
import com.back.facepick.auth.domain.PasswordEncryptor;
import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.auth.domain.RefreshTokenRepository;
import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidRefreshTokenException;
import com.back.facepick.user.application.UserCommandService;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.application.dto.api.UserInfo;
import com.back.facepick.user.application.dto.command.UserCreateCommand;
import com.back.facepick.user.application.dto.result.UserCreateResult;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthCommandService {
    private final CredentialRepository credentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final AuthTokenProvider tokenProvider;

    private final UserCommandService userCommandService;
    private final UserQueryApi userQueryApi;

    // 이메일 중복은 credentials.email 유니크 제약으로 막는다(CredentialRepositoryImpl.save).
    // 제약에 걸리면 같은 트랜잭션에서 먼저 만든 사용자도 함께 롤백된다.
    @Transactional
    public SignUpResult signUp(SignUpCommand command) {
        // 새 userId 로 곧바로 자격 증명과 토큰을 만들어야 해서 user 생성만은 이벤트가 아닌 동기 호출이다.
        // 아키텍처 테스트의 BoundedContexts.SYNC_COMMAND_ALLOWLIST 에 이 클래스가 등록돼 있다.
        UserCreateResult user = userCommandService.createUser(new UserCreateCommand(command.nickname()));
        credentialRepository.save(
                Credential.create(user.userId(), command.email(), command.password(), passwordEncryptor));
        Tokens tokens = issueTokens(user.userId(), user.authority(), LocalDateTime.now());
        return new SignUpResult(user.userId(), tokens.accessToken(), tokens.refreshToken(), user.createdAt());
    }

    @Transactional
    public LoginResult login(LoginCommand command) {
        // 이메일이 없을 때와 비밀번호가 틀릴 때 같은 예외를 던져 가입 여부를 알려 주지 않는다.
        Credential credential = credentialRepository
                .findByEmail(Credential.normalizeEmail(command.email()))
                .orElseThrow(AuthInvalidCredentialsException::new);
        credential.authenticate(command.password(), passwordEncryptor);
        UserInfo user = userQueryApi.getInfo(credential.getUserId());
        Tokens tokens = issueTokens(user.userId(), user.authority(), LocalDateTime.now());
        return new LoginResult(user.userId(), tokens.accessToken(), tokens.refreshToken());
    }

    @Transactional(readOnly = true)
    public TokenReissueResult reissueToken(TokenReissueCommand command) {
        Long userId = tokenProvider.getUserIdFromRefreshToken(command.refreshToken());
        // 서명이 유효해도 로그아웃으로 지운 토큰은 거부한다.
        if (!refreshTokenRepository.existsByTokenHash(RefreshToken.hash(command.refreshToken()))) {
            throw new AuthInvalidRefreshTokenException();
        }
        // 토큰에 담긴 권한을 믿지 않고 최신 권한을 다시 조회한다.
        UserInfo user = userQueryApi.getInfo(userId);
        return new TokenReissueResult(tokenProvider.createAccessToken(user.userId(), user.authority()));
    }

    // 이미 지웠거나 없는 토큰이어도 로그아웃은 성공으로 끝낸다.
    @Transactional
    public void logout(LogoutCommand command) {
        refreshTokenRepository.deleteByTokenHash(RefreshToken.hash(command.refreshToken()));
    }

    private Tokens issueTokens(Long userId, String role, LocalDateTime now) {
        String accessToken = tokenProvider.createAccessToken(userId, role);
        String refreshToken = tokenProvider.createRefreshToken(userId);
        LocalDateTime expiresAt = now.plus(Duration.ofMillis(tokenProvider.refreshTokenTtlMillis()));
        refreshTokenRepository.save(RefreshToken.create(userId, refreshToken, expiresAt));
        return new Tokens(accessToken, refreshToken);
    }

    private record Tokens(String accessToken, String refreshToken) {}
}
