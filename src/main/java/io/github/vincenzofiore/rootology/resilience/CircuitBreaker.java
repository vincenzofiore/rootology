package io.github.vincenzofiore.rootology.resilience;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A minimal circuit breaker for one external service (#29). CLOSED: calls go through. After
 * {@code failureThreshold} consecutive failures it opens: calls are refused for {@code openFor}, so
 * callers use their fallback at once instead of waiting for a timeout. Then one trial call is let
 * through (half-open): a success closes it, a failure opens it again. Thread-safe.
 * <p>
 * Calls timed with {@link #startCall()} are recorded as the timer {@code rootology.external.calls}, tagged with
 * the service and the outcome (#27); a breaker made without a meter registry (tests) records nothing.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public static final int FAILURE_THRESHOLD = 3;
    public static final Duration OPEN_FOR = Duration.ofMinutes(2);

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final String name;
    private final int failureThreshold;
    private final Duration openFor;
    private final Clock clock;
    private final MeterRegistry meters;

    private int failures;
    private Instant openUntil;
    private boolean trialInProgress;

    public CircuitBreaker(String name) {
        this(name, FAILURE_THRESHOLD, OPEN_FOR, Clock.systemUTC());
    }

    public CircuitBreaker(String name, int failureThreshold, Duration openFor, Clock clock) {
        this(name, failureThreshold, openFor, clock, new CompositeMeterRegistry());
    }

    CircuitBreaker(String name, MeterRegistry meters) {
        this(name, FAILURE_THRESHOLD, OPEN_FOR, Clock.systemUTC(), meters);
    }

    private CircuitBreaker(String name, int failureThreshold, Duration openFor, Clock clock, MeterRegistry meters) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openFor = openFor;
        this.clock = clock;
        this.meters = meters;
    }

    /** Starts timing one call; end it with {@link #success(Timer.Sample)} or {@link #failure(Timer.Sample)}. */
    public Timer.Sample startCall() {
        return Timer.start(meters);
    }

    public void success(Timer.Sample call) {
        record(call, "success");
        success();
    }

    public void failure(Timer.Sample call) {
        record(call, "failure");
        failure();
    }

    private void record(Timer.Sample call, String outcome) {
        call.stop(Timer.builder("rootology.external.calls").description("Calls to an external service")
                .tag("service", name).tag("outcome", outcome).register(meters));
    }

    /** True if a call may be made now; in half-open state only one caller gets true. */
    public synchronized boolean allowsCall() {
        if (openUntil == null) {
            return true;
        }
        if (clock.instant().isBefore(openUntil) || trialInProgress) {
            return false;
        }
        trialInProgress = true;
        return true;
    }

    public synchronized void success() {
        if (openUntil != null) {
            log.info("{} is available again: circuit closed", name);
        }
        failures = 0;
        openUntil = null;
        trialInProgress = false;
    }

    public synchronized void failure() {
        failures++;
        if (trialInProgress || failures >= failureThreshold) {
            openUntil = clock.instant().plus(openFor);
            trialInProgress = false;
            log.warn("{} failed {} time(s): circuit open until {}", name, failures, openUntil);
        }
    }

    public synchronized State state() {
        if (openUntil == null) {
            return State.CLOSED;
        }
        return clock.instant().isBefore(openUntil) && !trialInProgress ? State.OPEN : State.HALF_OPEN;
    }

    /** True after a failure that no success has followed yet. */
    public synchronized boolean failing() {
        return failures > 0;
    }

    public String name() {
        return name;
    }
}
