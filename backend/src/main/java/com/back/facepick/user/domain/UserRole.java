package com.back.facepick.user.domain;

public enum UserRole {
    USER("ROLE_USER");

    private final String authority;

    UserRole(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }
}
