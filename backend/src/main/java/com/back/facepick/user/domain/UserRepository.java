package com.back.facepick.user.domain;

import java.util.Collection;
import java.util.List;

public interface UserRepository {
    User save(User user);

    User getById(Long userId);

    List<User> findAllByIds(Collection<Long> userIds);
}
