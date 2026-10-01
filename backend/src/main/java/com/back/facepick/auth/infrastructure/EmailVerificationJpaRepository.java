package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.EmailVerification;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationJpaRepository extends JpaRepository<EmailVerification, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM EmailVerification v WHERE v.email = :email")
    Optional<EmailVerification> findByEmailForUpdate(@Param("email") String email);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM EmailVerification v WHERE v.email = :email AND v.codeHash = :codeHash")
    int deleteByEmailAndCodeHash(@Param("email") String email, @Param("codeHash") String codeHash);
}
