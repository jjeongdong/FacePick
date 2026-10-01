package com.back.facepick.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureRandomVerificationCodeGeneratorTest {

    private final SecureRandomVerificationCodeGenerator generator = new SecureRandomVerificationCodeGenerator();

    @Test
    @DisplayName("앞자리 0 을 포함한 6자리 숫자를 만든다")
    void generatesSixDigits() {
        // when
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            codes.add(generator.generate());
        }

        // then
        assertThat(codes).allMatch(code -> code.matches("\\d{6}"));
        assertThat(codes).anyMatch(code -> code.startsWith("0"));
    }
}
