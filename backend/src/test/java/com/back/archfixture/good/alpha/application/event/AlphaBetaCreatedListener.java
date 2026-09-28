package com.back.archfixture.good.alpha.application.event;

import com.back.archfixture.good.beta.domain.event.BetaCreatedEvent;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class AlphaBetaCreatedListener {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(BetaCreatedEvent event) {}
}
