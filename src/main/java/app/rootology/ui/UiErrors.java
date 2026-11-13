package app.rootology.ui;

import com.vaadin.flow.component.Component;
import app.rootology.spaces.SpaceAccessException;
import app.rootology.usage.UsageLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * What a view says when an action fails (#79). A failure a person caused — a validation, a limit, a refusal —
 * gets words they can act on; anything else gets a generic sentence with the request's trace id
 * ({@link TraceIdFilter}), so the log line can be found, and the log keeps the exception type only: its message
 * may carry coordinates, an account or facts. A service that is down is said by the view that catches it
 * ({@code error.claude}), since the UI never calls an external service on its own.
 */
final class UiErrors {

    private static final Logger log = LoggerFactory.getLogger(UiErrors.class);

    static String text(Component in, RuntimeException failure) {
        if (failure instanceof UsageLimitException limit) {
            return in.getTranslation("error.limit", limit.limit());
        }
        if (failure instanceof SpaceAccessException) {
            return in.getTranslation("error.refused");
        }
        if (failure instanceof IllegalArgumentException) {
            return in.getTranslation("error.invalid");
        }
        String traceId = MDC.get(TraceIdFilter.KEY);
        log.warn("A view could not do it ({}): {}", traceId, failure.getClass().getSimpleName());
        return traceId == null ? in.getTranslation("error.generic.notrace")
                : in.getTranslation("error.generic", traceId);
    }

    static void show(Component in, RuntimeException failure) {
        Toasts.problem(text(in, failure));
    }

    private UiErrors() {
    }
}
