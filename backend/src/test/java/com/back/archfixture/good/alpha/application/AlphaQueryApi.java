package com.back.archfixture.good.alpha.application;

import com.back.archfixture.good.beta.application.dto.api.BetaInfo;

// 타 BC 의 dto.api 는 QueryApi 에서도 쓸 수 있다.
public class AlphaQueryApi {
    /** alpha 가 보관한 beta 요약. */
    public BetaInfo getBeta(Long betaId) {
        return new BetaInfo(betaId, 1L);
    }
}
