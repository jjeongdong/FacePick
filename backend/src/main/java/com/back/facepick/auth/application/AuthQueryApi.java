package com.back.facepick.auth.application;

import com.back.facepick.auth.application.dto.api.CredentialInfo;
import com.back.facepick.auth.domain.Credential;
import com.back.facepick.auth.domain.CredentialRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthQueryApi {
    private final CredentialRepository credentialRepository;

    /**
     * 여러 사용자의 로그인 이메일을 한 번에 조회한다. 알림 메일 받는 주소로 쓴다. 없는 ID 는 결과에서 빠진다.
     *
     * @param userIds 사용자 ID 목록
     * @return 사용자 ID 를 키로 한 이메일 정보
     */
    @Transactional(readOnly = true)
    public Map<Long, CredentialInfo> getInfos(Collection<Long> userIds) {
        return credentialRepository.findAllByUserIds(userIds).stream()
                .collect(Collectors.toMap(Credential::getUserId, CredentialInfo::from));
    }
}
