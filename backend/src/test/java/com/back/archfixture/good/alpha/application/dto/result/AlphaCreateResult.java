package com.back.archfixture.good.alpha.application.dto.result;

import com.back.archfixture.good.alpha.domain.Alpha;

public record AlphaCreateResult(Long alphaId, WriterInfo writer) {
    public record WriterInfo(Long writerId) {}

    public static AlphaCreateResult from(Alpha alpha) {
        return new AlphaCreateResult(alpha.getId(), new WriterInfo(1L));
    }
}
