package com.back.facepick.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.back.facepick.user.application.dto.command.UserCreateCommand;
import com.back.facepick.user.application.dto.result.UserCreateResult;
import com.back.facepick.user.domain.User;
import com.back.facepick.user.domain.UserRepository;
import com.back.facepick.user.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserCommandService userCommandService;

    @Test
    @DisplayName("사용자를 만들고 ID·권한·생성 시각을 돌려준다")
    void createUser() {
        // given
        given(userRepository.save(any(User.class))).willReturn(UserFixture.user(1L, "민수"));

        // when
        UserCreateResult result = userCommandService.createUser(new UserCreateCommand("민수"));

        // then
        assertThat(result).isEqualTo(new UserCreateResult(1L, "ROLE_USER", UserFixture.CREATED_AT));
    }
}
