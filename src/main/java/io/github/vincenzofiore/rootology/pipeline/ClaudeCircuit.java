package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.resilience.CircuitBreaker;
import org.springframework.stereotype.Component;

/** The circuit breaker shared by every call to Claude (#29). */
@Component
class ClaudeCircuit {

    static final String UNAVAILABLE = "Claude is not available right now: please try again in a few minutes.";

    final CircuitBreaker breaker;

    ClaudeCircuit() {
        this(new CircuitBreaker("Claude"));
    }

    ClaudeCircuit(CircuitBreaker breaker) {
        this.breaker = breaker;
    }
}
