package com.back.facepick.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.back.facepick.auth.application.dto.api.CredentialInfo;
import com.back.facepick.auth.domain.CredentialRepository;
import com.back.facepick.auth.fixture.CredentialFixture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthQueryApiTest {

    @Mock
    private CredentialRepository credentialRepository;

    @InjectMocks
    private AuthQueryApi authQueryApi;

    @Test
    @DisplayName("사용자 ID 로 이메일을 모아 돌려준다. 없는 ID 는 빠진다")
    void getInfos() {
        // given
        given(credentialRepository.findAllByUserIds(List.of(1L, 2L, 3L)))
                .willReturn(List.of(
                        CredentialFixture.credential(1L, "a@example.com"),
                        CredentialFixture.credential(3L, "c@example.com")));

        // when
        Map<Long, CredentialInfo> infos = authQueryApi.getInfos(List.of(1L, 2L, 3L));

        // then
        assertThat(infos)
                .containsOnly(
                        Map.entry(1L, new CredentialInfo(1L, "a@example.com")),
                        Map.entry(3L, new CredentialInfo(3L, "c@example.com")));
    }
}
