package com.back.facepick.user.application.dto.api;

import com.back.facepick.user.domain.User;

public record UserInfo(Long userId, String nickname, String authority) {
    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getNickname(), user.getRole().authority());
    }
}
