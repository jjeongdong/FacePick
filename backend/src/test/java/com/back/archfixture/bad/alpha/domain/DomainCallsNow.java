package com.back.archfixture.bad.alpha.domain;

import java.time.LocalDateTime;

public class DomainCallsNow {
    LocalDateTime stamp() {
        return LocalDateTime.now();
    }
}
