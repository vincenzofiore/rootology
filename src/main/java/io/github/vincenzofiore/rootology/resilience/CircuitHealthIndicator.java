package io.github.vincenzofiore.rootology.resilience;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The external services as one health component (#27): DEGRADED, never DOWN, while a circuit is not closed,
 * since every caller has its fallback (#29) and the app keeps working. The details (each service's state)
 * are only for the logs and tests: the public health endpoint shows the status alone (#41).
 */
@Component("externalServices")
class CircuitHealthIndicator implements HealthIndicator {

    static final String DEGRADED = "DEGRADED";

    private final CircuitBreakers circuits;

    CircuitHealthIndicator(CircuitBreakers circuits) {
        this.circuits = circuits;
    }

    @Override
    public Health health() {
        Map<String, String> states = new LinkedHashMap<>();
        boolean degraded = false;
        for (CircuitBreaker breaker : circuits.all()) {
            states.put(breaker.name(), breaker.state().name());
            degraded |= breaker.state() != CircuitBreaker.State.CLOSED;
        }
        return (degraded ? Health.status(DEGRADED) : Health.up()).withDetails(states).build();
    }
}
