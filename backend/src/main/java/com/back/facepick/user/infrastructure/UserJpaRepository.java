package com.back.facepick.user.infrastructure;

import com.back.facepick.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {}
