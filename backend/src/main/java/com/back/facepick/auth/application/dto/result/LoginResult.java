package com.back.facepick.auth.application.dto.result;

public record LoginResult(Long userId, String accessToken, String refreshToken) {}
