package com.back.facepick.user.application;

import com.back.facepick.user.application.dto.result.UserResult;
import com.back.facepick.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserQueryService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResult getMyUser(Long userId) {
        return UserResult.from(userRepository.getById(userId));
    }
}
