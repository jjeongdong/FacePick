package com.back.facepick.global.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResendClientConfigTest {

    private final ResendClientConfig config = new ResendClientConfig();

    @Test
    @DisplayName("서킷 설정값이 그대로 들어가고 OPEN 뒤 자동으로 HALF_OPEN 이 된다")
    void circuitBreakerConfig() {
        // when
        CircuitBreakerConfig built =
                config.resendCircuitBreaker(10, 5, 50f, 3000, 50f, 60, 2).getCircuitBreakerConfig();

        // then
        assertThat(built.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
        assertThat(built.getSlidingWindowSize()).isEqualTo(10);
        assertThat(built.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(built.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(built.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(3));
        assertThat(built.getSlowCallRateThreshold()).isEqualTo(50f);
        assertThat(built.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
        assertThat(built.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();
    }

    @Test
    @DisplayName("잘못된 서킷 설정이면 기동이 실패한다")
    void rejectsInvalidConfig() {
        assertThatThrownBy(() -> config.resendCircuitBreaker(10, 5, 150f, 3000, 50f, 60, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
