package com.back.archfixture.bad.alpha.application;

import java.util.Optional;

public class UsesOptionalGet {
    Integer read() {
        return Optional.of(1).get();
    }
}
