package app.rootology.weather;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * wind-v1: a forecast step whose mean wind speed is at least 11 m/s (about 40 km/h). MET Norway's
 * Locationforecast has no gusts, and at 9 km the wind is unreliable in complex terrain, so the alert is
 * worded as a possibility, never as a certainty. Threshold approved by the owner on 2026-11-18.
 */
public final class WindRule {

    public static final String ID = "wind-v1";
    /** The mean wind speed that counts as strong, in m/s (11 m/s is about 40 km/h). */
    public static final double STRONG = 11.0;

    public static boolean strong(Double windSpeed) {
        return windSpeed != null && windSpeed >= STRONG;
    }

    /** The strongest step from now over the horizon, among those with a wind speed. */
    public static Optional<ForecastHour> strongest(Forecast forecast, Instant now, Duration horizon) {
        return forecast.hours().stream()
                .filter(h -> !h.time().isBefore(now.minus(Duration.ofHours(1))) && h.time().isBefore(now.plus(horizon)))
                .filter(h -> h.windSpeed() != null)
                .max(Comparator.comparingDouble(ForecastHour::windSpeed));
    }

    private WindRule() {
    }
}
