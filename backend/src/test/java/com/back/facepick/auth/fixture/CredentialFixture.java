package com.back.facepick.auth.fixture;

import com.back.facepick.auth.domain.Credential;

public final class CredentialFixture {

    public static final String PASSWORD = "password123";

    private CredentialFixture() {}

    public static Credential credential(Long userId, String email) {
        return Credential.create(userId, email, PASSWORD, new FakePasswordEncryptor());
    }
}
