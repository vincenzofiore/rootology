package io.github.vincenzofiore.rootology.resilience;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitHealthIndicatorTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final CircuitBreakers circuits = new CircuitBreakers(meters);
    private final CircuitBreaker jev = circuits.create("Jev");
    private final CircuitHealthIndicator health = new CircuitHealthIndicator(circuits);

    @Test
    void closedCircuitsAreUp() {
        circuits.create("Claude");

        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(health.health().getDetails()).containsEntry("Jev", "CLOSED").containsEntry("Claude", "CLOSED");
    }

    @Test
    void anOpenCircuitIsDegradedNotDown() {
        for (int i = 0; i < CircuitBreaker.FAILURE_THRESHOLD; i++) {
            jev.failure();
        }

        assertThat(health.health().getStatus().getCode()).isEqualTo(CircuitHealthIndicator.DEGRADED);
        assertThat(health.health().getDetails()).containsEntry("Jev", "OPEN");
    }

    @Test
    void timedCallsAreMeasuredByServiceAndOutcome() {
        jev.success(jev.startCall());
        jev.failure(jev.startCall());
        jev.failure(jev.startCall());

        assertThat(meters.get("rootology.external.calls").tags("service", "Jev", "outcome", "success").timer().count())
                .isEqualTo(1);
        assertThat(meters.get("rootology.external.calls").tags("service", "Jev", "outcome", "failure").timer().count())
                .isEqualTo(2);
    }
}
