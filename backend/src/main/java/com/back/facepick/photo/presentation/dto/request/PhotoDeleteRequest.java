package com.back.facepick.photo.presentation.dto.request;

import com.back.facepick.photo.application.dto.command.PhotoDeleteCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 원소에도 @NotNull 을 둔다. 없으면 [null] 이 서비스까지 가서 500 이 된다.
public record PhotoDeleteRequest(
        @NotEmpty(message = "삭제할 사진을 선택해주세요.") @Size(max = 100, message = "한 번에 100장까지 삭제할 수 있습니다.")
                List<@NotNull(message = "삭제할 사진 ID 가 올바르지 않습니다.") Long> photoIds) {

    public PhotoDeleteCommand toCommand() {
        return new PhotoDeleteCommand(photoIds);
    }
}
