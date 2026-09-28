package com.back.archfixture.good.alpha.application;

import com.back.archfixture.good.alpha.application.dto.command.AlphaCreateCommand;
import com.back.archfixture.good.alpha.application.dto.result.AlphaCreateResult;
import com.back.archfixture.good.alpha.domain.Alpha;
import com.back.archfixture.good.alpha.domain.AlphaRepository;
import com.back.archfixture.good.alpha.domain.event.AlphaCreatedEvent;
import com.back.archfixture.good.beta.application.BetaQueryApi;
import com.back.archfixture.good.beta.application.dto.api.BetaInfo;
import com.back.archfixture.good.global.infrastructure.SharedStorage;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

public class AlphaCommandService {
    private final AlphaRepository alphaRepository;
    private final BetaQueryApi betaQueryApi;
    private final ApplicationEventPublisher eventPublisher;
    private final SharedStorage sharedStorage;

    public AlphaCommandService(
            AlphaRepository alphaRepository,
            BetaQueryApi betaQueryApi,
            ApplicationEventPublisher eventPublisher,
            SharedStorage sharedStorage) {
        this.alphaRepository = alphaRepository;
        this.betaQueryApi = betaQueryApi;
        this.eventPublisher = eventPublisher;
        this.sharedStorage = sharedStorage;
    }

    @Transactional
    public AlphaCreateResult createAlpha(AlphaCreateCommand command, LocalDateTime now) {
        BetaInfo beta = betaQueryApi.getInfo(command.betaId());
        sharedStorage.upload("alpha");
        Alpha alpha = alphaRepository.save(Alpha.create(command.status(), now));
        eventPublisher.publishEvent(new AlphaCreatedEvent(alpha.getId()));
        return AlphaCreateResult.from(alpha);
    }
}
