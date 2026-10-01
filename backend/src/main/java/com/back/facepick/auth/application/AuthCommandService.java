package com.back.facepick.auth.application;

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
import com.back.facepick.auth.domain.PasswordEncryptor;
import com.back.facepick.auth.domain.RefreshToken;
import com.back.facepick.auth.domain.RefreshTokenRepository;
import com.back.facepick.auth.domain.VerificationCodeGenerator;
import com.back.facepick.auth.domain.VerificationMailSender;
import com.back.facepick.auth.domain.exception.AuthEmailAlreadyExistsException;
import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidRefreshTokenException;
import com.back.facepick.auth.domain.exception.AuthVerificationCodeExpiredException;
import com.back.facepick.auth.domain.exception.AuthVerificationCodeMismatchException;
import com.back.facepick.user.application.UserCommandService;
import com.back.facepick.user.application.UserQueryApi;
import com.back.facepick.user.application.dto.api.UserInfo;
import com.back.facepick.user.application.dto.command.UserCreateCommand;
import com.back.facepick.user.application.dto.result.UserCreateResult;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthCommandService {
    private final CredentialRepository credentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final AuthTokenProvider tokenProvider;
    private final EmailVerificationRepository emailVerificationRepository;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final VerificationMailSender verificationMailSender;
    // 인증 메일은 외부 호출이라 트랜잭션 밖에서 보내고, 틀린 코드 횟수는 가입이 실패해도 남아야 한다.
    // 한 메서드 안에서 트랜잭션을 나눠야 해서 @Transactional 대신 짧은 트랜잭션을 직접 연다.
    private final TransactionTemplate transactionTemplate;

    private final UserCommandService userCommandService;
    private final UserQueryApi userQueryApi;

    public AuthCommandService(
            CredentialRepository credentialRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncryptor passwordEncryptor,
            AuthTokenProvider tokenProvider,
            EmailVerificationRepository emailVerificationRepository,
            VerificationCodeGenerator verificationCodeGenerator,
            VerificationMailSender verificationMailSender,
            PlatformTransactionManager transactionManager,
            UserCommandService userCommandService,
            UserQueryApi userQueryApi) {
        this.credentialRepository = credentialRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncryptor = passwordEncryptor;
        this.tokenProvider = tokenProvider;
        this.emailVerificationRepository = emailVerificationRepository;
        this.verificationCodeGenerator = verificationCodeGenerator;
        this.verificationMailSender = verificationMailSender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.userCommandService = userCommandService;
        this.userQueryApi = userQueryApi;
    }

    public void sendEmailVerification(EmailVerificationSendCommand command) {
        String email = Credential.normalizeEmail(command.email());
        String code = verificationCodeGenerator.generate();
        transactionTemplate.executeWithoutResult(status -> issueCode(email, code, LocalDateTime.now()));
        try {
            verificationMailSender.send(email, code);
        } catch (RuntimeException e) {
            // 보내지 못한 코드를 지워 60초를 기다리지 않고 바로 다시 받을 수 있게 한다.
            // 그 사이 다른 요청이 새 코드를 발급했다면 해시가 달라 그 코드는 남는다.
            try {
                transactionTemplate.executeWithoutResult(status ->
                        emailVerificationRepository.deleteByEmailAndCodeHash(email, EmailVerification.hash(code)));
            } catch (RuntimeException cleanupFailure) {
                // 반납에 실패해도 사용자에게는 메일 실패(400·502)를 알려야 한다. 코드는 60초 뒤 재발급으로 덮인다.
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    // 이메일 중복은 credentials.email 유니크 제약으로 막는다(CredentialRepositoryImpl.save).
    // 제약에 걸리면 같은 트랜잭션에서 먼저 만든 사용자와 코드 소비도 함께 롤백된다.
    public SignUpResult signUp(SignUpCommand command) {
        String email = Credential.normalizeEmail(command.email());
        LocalDateTime now = LocalDateTime.now();
        // 틀린 횟수는 이 트랜잭션에서 커밋한 뒤 예외를 던진다. 가입과 한 트랜잭션이면 롤백돼 무차별 대입을 막지 못한다.
        boolean matched = Boolean.TRUE.equals(transactionTemplate.execute(status -> emailVerificationRepository
                .findByEmailForUpdate(email)
                .orElseThrow(AuthVerificationCodeExpiredException::new)
                .verify(command.code(), now)));
        if (!matched) {
            throw new AuthVerificationCodeMismatchException();
        }
        return transactionTemplate.execute(status -> createAccount(command, email, now));
    }

    private void issueCode(String email, String code, LocalDateTime now) {
        if (credentialRepository.findByEmail(email).isPresent()) {
            throw new AuthEmailAlreadyExistsException();
        }
        emailVerificationRepository
                .findByEmailForUpdate(email)
                .ifPresentOrElse(
                        verification -> verification.reissue(code, now),
                        () -> emailVerificationRepository.save(EmailVerification.create(email, code, now)));
    }

    private SignUpResult createAccount(SignUpCommand command, String email, LocalDateTime now) {
        // 확인한 코드가 아직 그대로일 때만 소비한다. 같은 코드로 동시에 가입하거나 그 사이 새 코드가 나갔으면 0행이다.
        if (emailVerificationRepository.deleteByEmailAndCodeHash(email, EmailVerification.hash(command.code())) == 0) {
            throw new AuthVerificationCodeExpiredException();
        }
        // 새 userId 로 곧바로 자격 증명과 토큰을 만들어야 해서 user 생성만은 이벤트가 아닌 동기 호출이다.
        // 아키텍처 테스트의 BoundedContexts.SYNC_COMMAND_ALLOWLIST 에 이 클래스가 등록돼 있다.
        UserCreateResult user = userCommandService.createUser(new UserCreateCommand(command.nickname()));
        credentialRepository.save(Credential.create(user.userId(), email, command.password(), passwordEncryptor));
        Tokens tokens = issueTokens(user.userId(), user.authority(), now);
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
