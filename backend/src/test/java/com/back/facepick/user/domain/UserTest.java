package com.back.facepick.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.facepick.user.domain.exception.UserInvalidNicknameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

    @Nested
    @DisplayName("사용자 생성")
    class Create {

        @Test
        @DisplayName("앞뒤 공백을 지운 닉네임과 일반 사용자 권한으로 생성된다")
        void createsWithTrimmedNickname() {
            // when
            User user = User.create("  민수 ");

            // then
            assertThat(user.getNickname()).isEqualTo("민수");
            assertThat(user.getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("빈 닉네임은 허용하지 않는다")
        void throwsWhenBlank() {
            // when & then
            assertThatThrownBy(() -> User.create("   ")).isInstanceOf(UserInvalidNicknameException.class);
        }

        @Test
        @DisplayName("20자를 넘는 닉네임은 허용하지 않는다")
        void throwsWhenTooLong() {
            // when & then
            assertThatThrownBy(() -> User.create("가".repeat(21))).isInstanceOf(UserInvalidNicknameException.class);
        }
    }
}
