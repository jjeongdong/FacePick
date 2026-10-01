package com.back.facepick.auth.presentation.dto.request;

import com.back.facepick.auth.application.dto.command.SignUpCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
                @Email(message = "이메일 형식이 올바르지 않습니다.")
                @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
                String email,
        @NotBlank(message = "비밀번호를 입력해주세요.") @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.") String password,
        @NotBlank(message = "닉네임을 입력해주세요.") @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.") String nickname,
        @NotBlank(message = "인증 코드를 입력해주세요.") @Pattern(regexp = "\\d{6}", message = "인증 코드는 6자리 숫자입니다.") String code) {
    public SignUpCommand toCommand() {
        return new SignUpCommand(email, password, nickname, code);
    }
}
