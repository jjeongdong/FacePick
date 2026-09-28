package com.back.facepick.auth.application.dto.result;

import java.time.LocalDateTime;

public record SignUpResult(Long userId, String accessToken, String refreshToken, LocalDateTime createdAt) {}
