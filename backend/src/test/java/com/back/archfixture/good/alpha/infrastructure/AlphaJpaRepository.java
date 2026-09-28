package com.back.archfixture.good.alpha.infrastructure;

import com.back.archfixture.good.alpha.domain.Alpha;
import java.util.Optional;

public interface AlphaJpaRepository {
    Optional<Alpha> findById(Long alphaId);

    Alpha save(Alpha alpha);
}
