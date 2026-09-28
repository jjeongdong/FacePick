package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BCryptPasswordEncryptorTest {

    private final BCryptPasswordEncryptor encryptor = new BCryptPasswordEncryptor();

    @Test
    @DisplayName("원문과 다른 해시를 만들고, 같은 비밀번호만 일치로 판단한다")
    void encryptsAndMatches() {
        // when
        String hash = encryptor.encrypt("password123");

        // then
        assertThat(hash).isNotEqualTo("password123");
        assertThat(encryptor.matches("password123", hash)).isTrue();
        assertThat(encryptor.matches("wrong-password", hash)).isFalse();
    }
}
