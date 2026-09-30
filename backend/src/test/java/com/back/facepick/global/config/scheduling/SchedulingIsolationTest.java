package com.back.facepick.global.config.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

// 외부 API·스토리지 작업이 스레드를 모두 붙잡아도 core 풀(outbox relay)은 계속 돈다.
@SpringJUnitConfig({SchedulingConfig.class, SchedulingIsolationTest.Jobs.class})
@TestPropertySource(
        properties = {
            "facepick.scheduling.core-pool-size=1",
            "facepick.scheduling.external-pool-size=2",
            "facepick.scheduling.storage-pool-size=3"
        })
class SchedulingIsolationTest {

    @Autowired
    private Jobs jobs;

    @AfterEach
    void release() {
        jobs.release.countDown();
    }

    @Test
    @DisplayName("external·storage 풀이 모두 막혀도 core 풀 작업은 자기 스레드에서 실행된다")
    void coreRunsWhileOthersAreBlocked() throws InterruptedException {
        // given — external 2개·storage 3개 작업이 스레드를 하나씩 붙잡고 풀리지 않는다
        assertThat(jobs.blockedStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        boolean coreRan = jobs.coreRan.await(5, TimeUnit.SECONDS);

        // then
        assertThat(coreRan).isTrue();
        assertThat(jobs.coreThread.get()).startsWith("sched-core-");
    }

    @Configuration(proxyBeanMethods = false)
    static class Jobs {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch blockedStarted = new CountDownLatch(5);
        final CountDownLatch coreRan = new CountDownLatch(1);
        final AtomicReference<String> coreThread = new AtomicReference<>();

        private void block() throws InterruptedException {
            blockedStarted.countDown();
            release.await();
        }

        @Scheduled(fixedDelay = 100, scheduler = SchedulerNames.EXTERNAL)
        void external1() throws InterruptedException {
            block();
        }

        @Scheduled(fixedDelay = 100, scheduler = SchedulerNames.EXTERNAL)
        void external2() throws InterruptedException {
            block();
        }

        @Scheduled(fixedDelay = 100, scheduler = SchedulerNames.STORAGE)
        void storage1() throws InterruptedException {
            block();
        }

        @Scheduled(fixedDelay = 100, scheduler = SchedulerNames.STORAGE)
        void storage2() throws InterruptedException {
            block();
        }

        @Scheduled(fixedDelay = 100, scheduler = SchedulerNames.STORAGE)
        void storage3() throws InterruptedException {
            block();
        }

        @Scheduled(initialDelay = 500, fixedDelay = 100, scheduler = SchedulerNames.CORE)
        void core() {
            coreThread.compareAndSet(null, Thread.currentThread().getName());
            coreRan.countDown();
        }
    }
}
