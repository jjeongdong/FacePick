package com.back.facepick.auth.domain;

import com.back.facepick.auth.domain.exception.AuthInvalidEmailException;
import com.back.facepick.auth.domain.exception.AuthVerificationAttemptsExceededException;
import com.back.facepick.auth.domain.exception.AuthVerificationCodeExpiredException;
import com.back.facepick.auth.domain.exception.AuthVerificationResendTooSoonException;
import com.back.facepick.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 이메일당 한 행. 새 코드를 보내면 같은 행을 덮어써 이전 코드는 자연히 무효가 된다.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "email_verifications")
public class EmailVerification extends BaseTimeEntity {
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    // 6자리 코드는 경우의 수가 100만이라 틀린 횟수를 막지 않으면 10분 안에 대입으로 뚫린다.
    private static final int MAX_ATTEMPTS = 5;
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int MAX_EMAIL_LENGTH = 254;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_verification_id")
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_EMAIL_LENGTH)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    private EmailVerification(String email, String code, LocalDateTime now) {
        this.email = email;
        issue(code, now);
    }

    public static EmailVerification create(String email, String code, LocalDateTime now) {
        String normalizedEmail = Credential.normalizeEmail(email);
        if (normalizedEmail.isEmpty() || normalizedEmail.length() > MAX_EMAIL_LENGTH) {
            throw new AuthInvalidEmailException();
        }
        return new EmailVerification(normalizedEmail, code, now);
    }

    public void reissue(String code, LocalDateTime now) {
        if (now.isBefore(lastSentAt.plus(RESEND_INTERVAL))) {
            throw new AuthVerificationResendTooSoonException();
        }
        issue(code, now);
    }

    // 틀리면 예외 대신 false 를 돌려준다. 틀린 횟수를 저장해야 하므로 호출하는 쪽이 커밋한 뒤 예외를 던진다.
    public boolean verify(String code, LocalDateTime now) {
        if (!now.isBefore(expiresAt)) {
            throw new AuthVerificationCodeExpiredException();
        }
        if (attempts >= MAX_ATTEMPTS) {
            throw new AuthVerificationAttemptsExceededException();
        }
        // 비교 시간이 일치하는 길이에 따라 달라지지 않게 한다.
        if (MessageDigest.isEqual(
                hash(code).getBytes(StandardCharsets.UTF_8), codeHash.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        attempts++;
        return false;
    }

    public static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 반드시 제공하는 알고리즘이라 실제로는 올 수 없다.
            throw new IllegalStateException(e);
        }
    }

    private void issue(String code, LocalDateTime now) {
        this.codeHash = hash(code);
        this.expiresAt = now.plus(CODE_TTL);
        this.attempts = 0;
        this.lastSentAt = now;
    }
}
