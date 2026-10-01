package com.back.facepick.auth.presentation.dto.request;

import com.back.facepick.auth.application.dto.command.EmailVerificationSendCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationSendRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
                @Email(message = "이메일 형식이 올바르지 않습니다.")
                @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
                String email) {
    public EmailVerificationSendCommand toCommand() {
        return new EmailVerificationSendCommand(email);
    }
}
