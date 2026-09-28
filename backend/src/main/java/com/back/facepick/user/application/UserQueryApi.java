package com.back.facepick.user.application;

import com.back.facepick.user.application.dto.api.UserInfo;
import com.back.facepick.user.domain.User;
import com.back.facepick.user.domain.UserRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserQueryApi {
    private final UserRepository userRepository;

    /**
     * 사용자 한 명의 공개 정보를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 사용자 ID·닉네임·권한
     * @throws com.back.facepick.user.domain.exception.UserNotFoundException 없는 사용자면 (404)
     */
    @Transactional(readOnly = true)
    public UserInfo getInfo(Long userId) {
        return UserInfo.from(userRepository.getById(userId));
    }

    /**
     * 여러 사용자의 공개 정보를 한 번에 조회한다. 없는 ID 는 결과에서 빠진다.
     *
     * @param userIds 사용자 ID 목록
     * @return 사용자 ID 를 키로 한 사용자 정보
     */
    @Transactional(readOnly = true)
    public Map<Long, UserInfo> getInfos(Collection<Long> userIds) {
        return userRepository.findAllByIds(userIds).stream().collect(Collectors.toMap(User::getId, UserInfo::from));
    }
}
