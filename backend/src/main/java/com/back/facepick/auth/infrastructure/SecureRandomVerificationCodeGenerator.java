package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.VerificationCodeGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomVerificationCodeGenerator implements VerificationCodeGenerator {
    private static final int CODE_BOUND = 1_000_000;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        return String.format("%06d", secureRandom.nextInt(CODE_BOUND));
    }
}
