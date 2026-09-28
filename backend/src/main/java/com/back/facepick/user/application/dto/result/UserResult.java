package com.back.facepick.user.application.dto.result;

import com.back.facepick.user.domain.User;

public record UserResult(Long userId, String nickname) {
    public static UserResult from(User user) {
        return new UserResult(user.getId(), user.getNickname());
    }
}
