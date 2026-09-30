package com.back.facepick.album.domain;

import java.time.LocalDateTime;

// 재시도해도 같은 메일이 되도록 알림 행·앨범에 고정된 값만 담는다 (발송 시각 금지).
public record ExpiryMail(String idempotencyKey, String to, Long albumId, String albumTitle, LocalDateTime expiresAt) {}
