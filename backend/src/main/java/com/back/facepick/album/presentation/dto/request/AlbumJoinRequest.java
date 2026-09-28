package com.back.facepick.album.presentation.dto.request;

import com.back.facepick.album.application.dto.command.AlbumJoinCommand;
import jakarta.validation.constraints.NotBlank;

public record AlbumJoinRequest(@NotBlank(message = "초대 코드를 입력해주세요.") String inviteCode) {
    public AlbumJoinCommand toCommand() {
        return new AlbumJoinCommand(inviteCode);
    }
}
