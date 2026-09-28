package com.back.facepick.auth.domain;

import java.util.Optional;

public interface CredentialRepository {

    // 같은 이메일이 이미 있으면 AuthEmailAlreadyExistsException.
    Credential save(Credential credential);

    Optional<Credential> findByEmail(String email);
}
