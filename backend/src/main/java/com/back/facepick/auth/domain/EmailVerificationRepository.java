package com.back.facepick.auth.domain;

import java.util.Optional;

public interface EmailVerificationRepository {

    // 같은 이메일로 동시에 요청이 와도 재발급·확인이 한 번씩 차례로 일어나게 행을 잠근다.
    Optional<EmailVerification> findByEmailForUpdate(String email);

    // 같은 이메일 행이 이미 있으면(동시 첫 요청) AuthVerificationResendTooSoonException.
    EmailVerification save(EmailVerification verification);

    // 해시까지 맞을 때만 지워, 그 사이 새로 발급된 코드는 남긴다. 지운 행 수를 돌려준다.
    int deleteByEmailAndCodeHash(String email, String codeHash);
}
