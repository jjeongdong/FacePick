package com.back.archfixture.bad.alpha.application;

import com.back.archfixture.bad.beta.domain.BetaEntity;

public class UsesOtherDomain {
    BetaEntity beta;

    Runnable task() {
        return new Runnable() {
            @Override
            public void run() {
                new BetaEntity();
            }
        };
    }
}
