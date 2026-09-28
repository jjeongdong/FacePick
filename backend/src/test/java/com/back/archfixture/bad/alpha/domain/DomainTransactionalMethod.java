package com.back.archfixture.bad.alpha.domain;

import org.springframework.transaction.annotation.Transactional;

public class DomainTransactionalMethod {
    @Transactional
    public void run() {}
}
