package com.back.facepick.user.application;

import com.back.facepick.user.application.dto.command.UserCreateCommand;
import com.back.facepick.user.application.dto.result.UserCreateResult;
import com.back.facepick.user.domain.User;
import com.back.facepick.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {
    private final UserRepository userRepository;

    @Transactional
    public UserCreateResult createUser(UserCreateCommand command) {
        User user = userRepository.save(User.create(command.nickname()));
        return UserCreateResult.from(user);
    }
}
