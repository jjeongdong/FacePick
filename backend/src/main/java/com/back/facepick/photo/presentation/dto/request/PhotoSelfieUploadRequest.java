package com.back.facepick.photo.presentation.dto.request;

import com.back.facepick.photo.application.dto.command.PhotoSelfieUploadCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

// 동의 값이 false 인 경우는 형식이 아닌 규칙이라 엔티티(Photo.validateSelfieFile)가 거절한다.
public record PhotoSelfieUploadRequest(
        @NotNull(message = "파일 해시 형식이 올바르지 않습니다.") @Pattern(regexp = "^[0-9a-f]{64}$", message = "파일 해시 형식이 올바르지 않습니다.")
                String contentHash,
        @NotNull(message = "파일 크기를 입력해주세요.") @Positive(message = "파일 크기는 1바이트 이상이어야 합니다.") Long byteSize,
        @NotBlank(message = "파일 형식을 입력해주세요.") String contentType,
        @NotNull(message = "얼굴 분석 동의 여부를 입력해주세요.") Boolean faceAnalysisConsent) {

    public PhotoSelfieUploadCommand toCommand() {
        return new PhotoSelfieUploadCommand(contentHash, byteSize, contentType, faceAnalysisConsent);
    }
}
