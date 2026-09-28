package com.back.facepick.auth.presentation.dto.request;

import com.back.facepick.auth.application.dto.command.TokenReissueCommand;
import jakarta.validation.constraints.NotBlank;

public record TokenReissueRequest(@NotBlank(message = "리프레시 토큰을 입력해주세요.") String refreshToken) {
    public TokenReissueCommand toCommand() {
        return new TokenReissueCommand(refreshToken);
    }
}
