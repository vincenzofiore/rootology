package io.github.vincenzofiore.rootology.weather;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/**
 * frost-risk-v1: sowing or planting a crop now is not advised when the forecast minimum air temperature
 * over the next 7 days is within MARGIN degrees of the temperature that kills its young plants
 * (ECOCROP, "killing temp. early growth"). Crops without that value are not judged. Air temperature
 * is measured at 2 m, so ground frost can come earlier: hence the margin. A starting point, not tuned.
 */
public final class FrostRule {

    public static final String ID = "frost-risk-v1";
    public static final Duration HORIZON = Duration.ofDays(7);
    public static final double MARGIN = 1.0;

    /** The coldest forecast step from now over the horizon. */
    public static Optional<ForecastHour> coldest(Forecast forecast, Instant now) {
        return forecast.hours().stream()
                .filter(h -> !h.time().isBefore(now.minus(Duration.ofHours(1))) && h.time().isBefore(now.plus(HORIZON)))
                .min(Comparator.comparingDouble(ForecastHour::airTemperature));
    }

    public static boolean tooCold(double minimum, double killingTemperature) {
        return minimum <= killingTemperature + MARGIN;
    }

    private FrostRule() {
    }
}
