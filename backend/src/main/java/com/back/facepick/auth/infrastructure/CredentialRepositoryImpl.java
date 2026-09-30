package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.Credential;
import com.back.facepick.auth.domain.CredentialRepository;
import com.back.facepick.auth.domain.exception.AuthEmailAlreadyExistsException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CredentialRepositoryImpl implements CredentialRepository {
    private final CredentialJpaRepository credentialJpaRepository;

    // 이메일 중복은 미리 조회하지 않고 유니크 제약으로 막는다. 동시에 같은 이메일로 가입해도 한쪽만 성공한다.
    // saveAndFlush 로 INSERT 를 즉시 실행해 위반을 이 자리에서 잡는다.
    @Override
    public Credential save(Credential credential) {
        try {
            return credentialJpaRepository.saveAndFlush(credential);
        } catch (DataIntegrityViolationException e) {
            throw new AuthEmailAlreadyExistsException();
        }
    }

    @Override
    public Optional<Credential> findByEmail(String email) {
        return credentialJpaRepository.findByEmail(email);
    }

    @Override
    public List<Credential> findAllByUserIds(Collection<Long> userIds) {
        return credentialJpaRepository.findAllByUserIdIn(userIds);
    }
}
