package com.back.facepick.album.domain;

// 도메인이 난수 생성 방식을 모르게 하려고 둔 포트. 구현은 infrastructure 의 SecureRandomInviteCodeGenerator.
public interface InviteCodeGenerator {
    String generate();
}
