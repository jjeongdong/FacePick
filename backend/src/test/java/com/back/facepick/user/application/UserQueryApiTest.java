package com.back.facepick.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.back.facepick.user.application.dto.api.UserInfo;
import com.back.facepick.user.domain.UserRepository;
import com.back.facepick.user.fixture.UserFixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserQueryApiTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserQueryApi userQueryApi;

    @Test
    @DisplayName("사용자 한 명의 공개 정보를 조회한다")
    void getInfo() {
        // given
        given(userRepository.getById(1L)).willReturn(UserFixture.user(1L, "민수"));

        // when
        UserInfo info = userQueryApi.getInfo(1L);

        // then
        assertThat(info).isEqualTo(new UserInfo(1L, "민수", "ROLE_USER"));
    }

    @Test
    @DisplayName("여러 사용자 정보를 ID 로 찾을 수 있는 Map 으로 돌려준다")
    void getInfos() {
        // given
        given(userRepository.findAllByIds(List.of(1L, 2L)))
                .willReturn(List.of(UserFixture.user(1L, "민수"), UserFixture.user(2L, "지영")));

        // when
        Map<Long, UserInfo> infosById = userQueryApi.getInfos(List.of(1L, 2L));

        // then
        assertThat(infosById).containsOnlyKeys(1L, 2L);
        assertThat(infosById.get(2L).nickname()).isEqualTo("지영");
    }
}
