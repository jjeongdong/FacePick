package com.back.facepick.auth.application.dto.api;

import com.back.facepick.auth.domain.Credential;

public record CredentialInfo(Long userId, String email) {
    public static CredentialInfo from(Credential credential) {
        return new CredentialInfo(credential.getUserId(), credential.getEmail());
    }
}
