package com.back.facepick.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorTypeTest {

    @ParameterizedTest
    @CsvSource({
        "INVALID, false",
        "UNAUTHORIZED, false",
        "FORBIDDEN, false",
        "NOT_FOUND, false",
        "CONFLICT, false",
        "INTERNAL, true",
        "EXTERNAL, true"
    })
    @DisplayName("서버 원인 실패는 INTERNAL 과 EXTERNAL 뿐이다")
    void isServerFaultOnlyForInternalAndExternal(ErrorType type, boolean expected) {
        // when
        boolean serverFault = type.isServerFault();

        // then
        assertThat(serverFault).isEqualTo(expected);
    }
}
