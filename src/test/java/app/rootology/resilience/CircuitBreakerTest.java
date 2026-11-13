package app.rootology.resilience;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerTest {

    /** A clock the test moves by hand. */
    static final class ManualClock extends Clock {
        Instant now = Instant.parse("2026-11-04T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final CircuitBreaker breaker = new CircuitBreaker("test", 3, Duration.ofMinutes(2), clock);

    @Test
    void opensAfterConsecutiveFailuresOnly() {
        breaker.failure();
        breaker.failure();
        breaker.success();
        breaker.failure();
        breaker.failure();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.allowsCall()).isTrue();

        breaker.failure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.allowsCall()).isFalse();
    }

    @Test
    void afterTheWaitOneTrialDecides() {
        for (int i = 0; i < 3; i++) {
            breaker.failure();
        }
        clock.now = clock.now.plus(Duration.ofMinutes(2));

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.allowsCall()).isTrue();
        assertThat(breaker.allowsCall()).as("only one trial at a time").isFalse();

        breaker.failure();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        clock.now = clock.now.plus(Duration.ofMinutes(2));
        assertThat(breaker.allowsCall()).isTrue();
        breaker.success();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.failing()).isFalse();
        assertThat(breaker.allowsCall()).isTrue();
    }
}
