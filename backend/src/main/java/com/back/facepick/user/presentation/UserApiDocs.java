package com.back.facepick.user.presentation;

import com.back.facepick.user.application.dto.result.UserResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "[사용자] 사용자 API")
public interface UserApiDocs {

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 ID 와 닉네임을 조회합니다.")
    ResponseEntity<UserResult> getMyUser(@Parameter(hidden = true) Long userId);
}
