package com.back.facepick.album.infrastructure;

import com.back.facepick.album.domain.InviteCodeGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomInviteCodeGenerator implements InviteCodeGenerator {
    // 128비트면 추측도 충돌도 무시할 수 있어 중복 재시도를 두지 않는다.
    private static final int CODE_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
