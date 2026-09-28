package com.back.facepick.photo.presentation.dto.request;

import com.back.facepick.photo.application.dto.command.PhotoUploadCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PhotoUploadRequest(
        @NotEmpty(message = "업로드할 파일을 선택해주세요.") @Size(max = 100, message = "한 번에 100장까지 올릴 수 있습니다.")
                List<@Valid UploadFile> files) {

    public record UploadFile(
            @NotNull(message = "파일 해시 형식이 올바르지 않습니다.")
                    @Pattern(regexp = "^[0-9a-f]{64}$", message = "파일 해시 형식이 올바르지 않습니다.")
                    String contentHash,
            @NotNull(message = "파일 크기를 입력해주세요.") @Positive(message = "파일 크기는 1바이트 이상이어야 합니다.") Long byteSize,
            @NotBlank(message = "파일 형식을 입력해주세요.") String contentType) {}

    public PhotoUploadCommand toCommand() {
        return new PhotoUploadCommand(files.stream()
                .map(file -> new PhotoUploadCommand.UploadFile(file.contentHash(), file.byteSize(), file.contentType()))
                .toList());
    }
}
