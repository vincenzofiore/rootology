package app.rootology.resilience;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Creates the app's circuit breakers, one per external service, and keeps them, so their state can be
 * shown by the health check and watched by the operational alerts (#27). Their calls are measured in the
 * app's meter registry.
 */
@Component
public class CircuitBreakers {

    private final MeterRegistry meters;
    private final List<CircuitBreaker> all = new CopyOnWriteArrayList<>();

    public CircuitBreakers(MeterRegistry meters) {
        this.meters = meters;
    }

    public CircuitBreaker create(String service) {
        CircuitBreaker breaker = new CircuitBreaker(service, meters);
        all.add(breaker);
        return breaker;
    }

    /** The breaker of one service, created on first use: one external service has one circuit (#29, #53). */
    public CircuitBreaker forService(String service) {
        return all.stream().filter(breaker -> breaker.name().equals(service)).findFirst()
                .orElseGet(() -> create(service));
    }

    public List<CircuitBreaker> all() {
        return List.copyOf(all);
    }
}
