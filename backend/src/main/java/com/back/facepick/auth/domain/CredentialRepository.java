package com.back.facepick.auth.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CredentialRepository {

    // 같은 이메일이 이미 있으면 AuthEmailAlreadyExistsException.
    Credential save(Credential credential);

    Optional<Credential> findByEmail(String email);

    List<Credential> findAllByUserIds(Collection<Long> userIds);
}
