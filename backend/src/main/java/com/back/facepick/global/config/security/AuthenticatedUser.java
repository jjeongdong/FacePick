package com.back.facepick.global.config.security;

public record AuthenticatedUser(Long userId, String role) {}
