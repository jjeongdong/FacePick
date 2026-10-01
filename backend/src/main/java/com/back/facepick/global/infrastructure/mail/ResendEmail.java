package com.back.facepick.global.infrastructure.mail;

// idempotencyKey 가 null 이면 Idempotency-Key 헤더를 붙이지 않는다 (재시도하지 않는 메일).
public record ResendEmail(String to, String subject, String text, String html, String idempotencyKey) {}
