package com.back.facepick.global.config.scheduling;

import java.util.Set;

// 스케줄 작업을 외부 의존성별 풀로 나눈다(벌크헤드). 한 풀의 작업이 외부 장애로 스레드를 붙잡아도 다른 풀은 돈다.
public final class SchedulerNames {
    // 업로드 파이프라인 (outbox relay). 외부 API·스토리지 장애에 막히면 안 된다.
    public static final String CORE = "coreScheduler";
    // 외부 HTTP API (만료 알림 메일)
    public static final String EXTERNAL = "externalScheduler";
    // S3 스토리지 정리와 그 준비 (파일 삭제, 만료 앨범 삭제)
    public static final String STORAGE = "storageScheduler";

    public static final Set<String> ALL = Set.of(CORE, EXTERNAL, STORAGE);

    private SchedulerNames() {}
}
