package com.back.facepick.user.application.dto.result;

import com.back.facepick.user.domain.User;
import java.time.LocalDateTime;

public record UserCreateResult(Long userId, String authority, LocalDateTime createdAt) {
    public static UserCreateResult from(User user) {
        return new UserCreateResult(user.getId(), user.getRole().authority(), user.getCreatedAt());
    }
}
