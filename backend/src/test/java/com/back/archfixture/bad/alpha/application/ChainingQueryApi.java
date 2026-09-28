package com.back.archfixture.bad.alpha.application;

import com.back.archfixture.bad.beta.application.BetaQueryApi;

// QueryApi 가 타 BC 의 QueryApi 를 부르면 조회가 연쇄되고 빈 순환이 생길 수 있다.
public class ChainingQueryApi {
    private final BetaQueryApi betaQueryApi;

    public ChainingQueryApi(BetaQueryApi betaQueryApi) {
        this.betaQueryApi = betaQueryApi;
    }

    public String getName(Long betaId) {
        return betaQueryApi.getName(betaId);
    }
}
