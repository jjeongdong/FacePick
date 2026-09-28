package com.back.facepick.user.infrastructure;

import com.back.facepick.user.domain.User;
import com.back.facepick.user.domain.UserRepository;
import com.back.facepick.user.domain.exception.UserNotFoundException;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public User getById(Long userId) {
        return userJpaRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    @Override
    public List<User> findAllByIds(Collection<Long> userIds) {
        return userJpaRepository.findAllById(userIds);
    }
}
