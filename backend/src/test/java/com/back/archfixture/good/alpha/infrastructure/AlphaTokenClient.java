package com.back.archfixture.good.alpha.infrastructure;

import java.time.Instant;
import java.util.Date;

// 외부 라이브러리가 Date 를 요구하는 경우를 흉내 낸다.
public class AlphaTokenClient {
    public Date expiration(Instant expiresAt) {
        return Date.from(expiresAt);
    }
}
