package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.EmailVerification;
import com.back.facepick.auth.domain.EmailVerificationRepository;
import com.back.facepick.auth.domain.exception.AuthVerificationResendTooSoonException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepositoryImpl implements EmailVerificationRepository {
    private final EmailVerificationJpaRepository emailVerificationJpaRepository;

    @Override
    public Optional<EmailVerification> findByEmailForUpdate(String email) {
        return emailVerificationJpaRepository.findByEmailForUpdate(email);
    }

    // 행이 없을 때는 잠글 대상이 없어 같은 이메일의 첫 요청 둘이 함께 들어올 수 있다.
    // 유니크 제약으로 하나만 통과시키고, 진 쪽은 방금 다른 요청이 코드를 보낸 것이므로 재발송 간격 위반으로 본다.
    @Override
    public EmailVerification save(EmailVerification verification) {
        try {
            return emailVerificationJpaRepository.saveAndFlush(verification);
        } catch (DataIntegrityViolationException e) {
            throw new AuthVerificationResendTooSoonException();
        }
    }

    @Override
    public int deleteByEmailAndCodeHash(String email, String codeHash) {
        return emailVerificationJpaRepository.deleteByEmailAndCodeHash(email, codeHash);
    }
}
