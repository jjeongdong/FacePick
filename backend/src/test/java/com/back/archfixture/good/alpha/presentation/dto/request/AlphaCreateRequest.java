package com.back.archfixture.good.alpha.presentation.dto.request;

import com.back.archfixture.good.alpha.application.dto.command.AlphaCreateCommand;
import com.back.archfixture.good.alpha.domain.AlphaStatus;

public record AlphaCreateRequest(AlphaStatus status, Long betaId) {
    public AlphaCreateCommand toCommand() {
        return new AlphaCreateCommand(status, betaId);
    }
}
