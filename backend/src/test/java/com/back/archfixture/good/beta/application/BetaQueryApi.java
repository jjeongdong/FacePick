package com.back.archfixture.good.beta.application;

import com.back.archfixture.good.beta.application.dto.api.BetaInfo;

public class BetaQueryApi {
    /** beta 단건 공개 정보. 없으면 예외. */
    public BetaInfo getInfo(Long betaId) {
        return new BetaInfo(betaId, 1L);
    }
}
