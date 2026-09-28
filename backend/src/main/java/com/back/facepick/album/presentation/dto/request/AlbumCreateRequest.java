package com.back.facepick.album.presentation.dto.request;

import com.back.facepick.album.application.dto.command.AlbumCreateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlbumCreateRequest(
        @NotBlank(message = "앨범 제목을 입력해주세요.") @Size(max = 50, message = "앨범 제목은 50자 이하여야 합니다.") String title) {
    public AlbumCreateCommand toCommand() {
        return new AlbumCreateCommand(title);
    }
}
