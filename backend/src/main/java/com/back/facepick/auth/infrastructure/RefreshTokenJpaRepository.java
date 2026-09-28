package com.back.facepick.auth.infrastructure;

import com.back.facepick.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {
    boolean existsByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
    void deleteByTokenHash(@Param("tokenHash") String tokenHash);
}
