package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.Credential;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialJpaRepository extends JpaRepository<Credential, Long> {
    Optional<Credential> findByEmail(String email);

    List<Credential> findAllByUserIdIn(Collection<Long> userIds);
}
