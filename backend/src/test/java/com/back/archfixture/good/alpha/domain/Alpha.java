package com.back.archfixture.good.alpha.domain;

import java.time.LocalDateTime;

public class Alpha {
    private Long id;
    private AlphaStatus status;
    private LocalDateTime createdAt;

    private Alpha(AlphaStatus status, LocalDateTime createdAt) {
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Alpha create(AlphaStatus status, LocalDateTime now) {
        return new Alpha(status, now);
    }

    public void close() {
        this.status = AlphaStatus.CLOSED;
    }

    public Long getId() {
        return id;
    }

    public AlphaStatus getStatus() {
        return status;
    }
}
