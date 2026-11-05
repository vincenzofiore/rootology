package io.github.vincenzofiore.rootology.weather;

import java.time.Instant;
import java.util.List;

/**
 * The weather around a location, in time order: past hours (from the forecasts made just before them)
 * and the coming days. stale is true when the service could not be reached and an older copy is used.
 */
public record Forecast(List<ForecastHour> hours, Instant fetchedAt, boolean stale) {

    public static final String SOURCE = "met-norway";

    public Forecast {
        hours = List.copyOf(hours);
    }
}
