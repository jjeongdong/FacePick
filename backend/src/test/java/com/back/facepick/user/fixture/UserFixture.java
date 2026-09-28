package com.back.facepick.user.fixture;

import com.back.facepick.user.domain.User;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserFixture {

    public static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 12, 0);

    private UserFixture() {}

    public static User user(Long userId, String nickname) {
        User user = User.create(nickname);
        // 저장 없이 쓰는 단위 테스트용이라 id·생성 시각을 리플렉션으로 채운다.
        ReflectionTestUtils.setField(user, "id", userId);
        ReflectionTestUtils.setField(user, "createdAt", CREATED_AT);
        return user;
    }
}
