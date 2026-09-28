package com.back.facepick.global.config.security;

import com.back.facepick.global.error.ErrorCode;
import com.back.facepick.global.error.ErrorHttpStatus;
import com.back.facepick.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

// 보안 필터 단계의 실패는 GlobalExceptionHandler 에 닿지 않으므로, 같은 본문 형식을 여기서 직접 쓴다.
final class SecurityErrorResponses {

    private SecurityErrorResponses() {}

    static void write(HttpServletResponse response, JsonMapper jsonMapper, ErrorCode errorCode) throws IOException {
        response.setStatus(ErrorHttpStatus.of(errorCode.type()).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.from(errorCode)));
    }
}
