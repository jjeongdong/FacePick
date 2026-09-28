package com.back.facepick.album.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureRandomInviteCodeGeneratorTest {

    private final SecureRandomInviteCodeGenerator generator = new SecureRandomInviteCodeGenerator();

    @Test
    @DisplayName("URL 에 그대로 넣을 수 있는 22자 코드를 만든다")
    void generatesUrlSafeCode() {
        // when
        String code = generator.generate();

        // then
        assertThat(code).matches("[A-Za-z0-9_-]{22}");
    }

    @Test
    @DisplayName("부를 때마다 다른 코드를 만든다")
    void generatesDifferentCodes() {
        // when
        String first = generator.generate();
        String second = generator.generate();

        // then
        assertThat(first).isNotEqualTo(second);
    }
}
