package com.back.archfixture.good.alpha.application.dto.command;

import com.back.archfixture.good.alpha.domain.AlphaStatus;

public record AlphaCreateCommand(AlphaStatus status, Long betaId) {}
