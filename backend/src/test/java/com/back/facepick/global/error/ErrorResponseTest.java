package com.back.facepick.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    @DisplayName("에러 코드의 이름과 기본 메시지로 응답을 만든다")
    void createsFromErrorCode() {
        // when
        ErrorResponse response = ErrorResponse.from(GlobalErrorCode.FORBIDDEN);

        // then
        assertThat(response).isEqualTo(new ErrorResponse("FORBIDDEN", "접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("메시지를 바꿔 끼울 수 있다")
    void createsWithCustomMessage() {
        // when
        ErrorResponse response = ErrorResponse.of(GlobalErrorCode.INVALID_INPUT, "이름은 필수입니다.");

        // then
        assertThat(response).isEqualTo(new ErrorResponse("INVALID_INPUT", "이름은 필수입니다."));
    }
}
