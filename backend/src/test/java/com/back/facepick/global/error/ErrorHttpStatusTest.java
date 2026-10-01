package com.back.facepick.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

class ErrorHttpStatusTest {

    @ParameterizedTest
    @CsvSource({
        "INVALID, BAD_REQUEST",
        "UNAUTHORIZED, UNAUTHORIZED",
        "FORBIDDEN, FORBIDDEN",
        "NOT_FOUND, NOT_FOUND",
        "CONFLICT, CONFLICT",
        "TOO_MANY_REQUESTS, TOO_MANY_REQUESTS",
        "INTERNAL, INTERNAL_SERVER_ERROR",
        "EXTERNAL, BAD_GATEWAY"
    })
    @DisplayName("ErrorType 을 HTTP 상태로 변환한다")
    void mapsErrorTypeToHttpStatus(ErrorType type, HttpStatus expected) {
        // when
        HttpStatus status = ErrorHttpStatus.of(type);

        // then
        assertThat(status).isEqualTo(expected);
    }
}
