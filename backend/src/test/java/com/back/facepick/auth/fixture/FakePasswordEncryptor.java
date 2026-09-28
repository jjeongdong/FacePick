package com.back.facepick.auth.fixture;

import com.back.facepick.auth.domain.PasswordEncryptor;
import java.util.Objects;

// BCrypt 는 느리고 결과가 매번 달라 단위 테스트에서는 예측 가능한 가짜 해시를 쓴다.
public final class FakePasswordEncryptor implements PasswordEncryptor {
    private static final String PREFIX = "hashed:";

    @Override
    public String encrypt(String rawPassword) {
        return PREFIX + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return Objects.equals(PREFIX + rawPassword, passwordHash);
    }
}
