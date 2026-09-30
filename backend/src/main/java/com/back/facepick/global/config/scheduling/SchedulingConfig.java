package com.back.facepick.global.config.scheduling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

// 작업마다 fixedDelay 라 같은 작업은 겹쳐 돌지 않는다. 풀 크기 = 그 풀의 작업 수면 서로 기다리지 않는다.
// 모든 @Scheduled 는 scheduler 를 지정해야 한다 (SchedulingRuleTest).
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean(name = SchedulerNames.CORE)
    public ThreadPoolTaskScheduler coreScheduler(@Value("${facepick.scheduling.core-pool-size}") int poolSize) {
        return scheduler(poolSize, "sched-core-");
    }

    @Bean(name = SchedulerNames.EXTERNAL)
    public ThreadPoolTaskScheduler externalScheduler(@Value("${facepick.scheduling.external-pool-size}") int poolSize) {
        return scheduler(poolSize, "sched-external-");
    }

    @Bean(name = SchedulerNames.STORAGE)
    public ThreadPoolTaskScheduler storageScheduler(@Value("${facepick.scheduling.storage-pool-size}") int poolSize) {
        return scheduler(poolSize, "sched-storage-");
    }

    private static ThreadPoolTaskScheduler scheduler(int poolSize, String threadNamePrefix) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        return scheduler;
    }
}
