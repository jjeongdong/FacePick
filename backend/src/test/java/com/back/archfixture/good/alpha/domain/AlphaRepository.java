package com.back.archfixture.good.alpha.domain;

public interface AlphaRepository {
    Alpha getById(Long alphaId);

    Alpha save(Alpha alpha);
}
