package com.back.facepick.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.back.facepick.user.application.dto.result.UserResult;
import com.back.facepick.user.domain.UserRepository;
import com.back.facepick.user.fixture.UserFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserQueryService userQueryService;

    @Test
    @DisplayName("내 정보를 조회한다")
    void getMyUser() {
        // given
        given(userRepository.getById(1L)).willReturn(UserFixture.user(1L, "민수"));

        // when
        UserResult result = userQueryService.getMyUser(1L);

        // then
        assertThat(result).isEqualTo(new UserResult(1L, "민수"));
    }
}
