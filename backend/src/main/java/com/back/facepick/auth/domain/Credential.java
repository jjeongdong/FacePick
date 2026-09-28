package com.back.facepick.auth.domain;

import com.back.facepick.auth.domain.exception.AuthInvalidCredentialsException;
import com.back.facepick.auth.domain.exception.AuthInvalidEmailException;
import com.back.facepick.auth.domain.exception.AuthInvalidPasswordException;
import com.back.facepick.global.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "credentials")
public class Credential extends BaseTimeEntity {
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_LENGTH = 8;
    // BCrypt 는 72바이트를 넘는 입력을 받지 않는다(Spring Security 가 예외를 던진다).
    // 한글은 한 글자가 3바이트라 글자 수가 아니라 바이트로 막는다.
    private static final int MAX_PASSWORD_BYTES = 72;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credential_id")
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, unique = true, length = MAX_EMAIL_LENGTH)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    private Credential(Long userId, String email, String passwordHash) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public static Credential create(
            Long userId, String email, String rawPassword, PasswordEncryptor passwordEncryptor) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty() || normalizedEmail.length() > MAX_EMAIL_LENGTH) {
            throw new AuthInvalidEmailException();
        }
        if (rawPassword == null
                || rawPassword.length() < MIN_PASSWORD_LENGTH
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new AuthInvalidPasswordException();
        }
        return new Credential(userId, normalizedEmail, passwordEncryptor.encrypt(rawPassword));
    }

    // 대소문자만 다른 이메일로 중복 가입하지 않도록 저장과 조회 모두 같은 형태로 맞춘다.
    public static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public void authenticate(String rawPassword, PasswordEncryptor passwordEncryptor) {
        if (!passwordEncryptor.matches(rawPassword, passwordHash)) {
            throw new AuthInvalidCredentialsException();
        }
    }
}
