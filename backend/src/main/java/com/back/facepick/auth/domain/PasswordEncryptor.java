package com.back.facepick.auth.domain;

// 도메인이 Spring Security 를 모르게 하려고 둔 포트. 구현은 infrastructure 의 BCryptPasswordEncryptor.
public interface PasswordEncryptor {
    String encrypt(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
