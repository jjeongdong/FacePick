package com.back.archfixture.good.alpha.infrastructure;

import com.back.archfixture.good.alpha.domain.Alpha;
import com.back.archfixture.good.alpha.domain.AlphaRepository;
import com.back.archfixture.good.alpha.domain.exception.AlphaNotFoundException;

public class AlphaRepositoryImpl implements AlphaRepository {
    private final AlphaJpaRepository alphaJpaRepository;

    public AlphaRepositoryImpl(AlphaJpaRepository alphaJpaRepository) {
        this.alphaJpaRepository = alphaJpaRepository;
    }

    @Override
    public Alpha getById(Long alphaId) {
        return alphaJpaRepository.findById(alphaId).orElseThrow(AlphaNotFoundException::new);
    }

    @Override
    public Alpha save(Alpha alpha) {
        return alphaJpaRepository.save(alpha);
    }
}
