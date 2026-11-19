package app.rootology.weather;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Optional;

/**
 * heavy-rain-v1: a day is heavy when it totals at least 30 mm, and an hour is heavy when a single hourly
 * step brings at least 10 mm. Only hours of one step count for the hourly threshold: further ahead MET Norway
 * gives 6-hour steps, whose total says nothing about an hour. Thresholds approved by the owner on 2026-11-18.
 */
public final class HeavyRainRule {

    public static final String ID = "heavy-rain-v1";
    /** A day's total precipitation that counts as heavy, in mm. */
    public static final double DAY = 30.0;
    /** One hour's precipitation that counts as heavy, in mm. */
    public static final double HOUR = 10.0;

    public static boolean heavyDay(double millimetres) {
        return millimetres >= DAY;
    }

    /** Whether this step is one hour and brings at least {@link #HOUR} millimetres. */
    public static boolean heavyHour(ForecastHour hour) {
        return hour.stepHours() == 1 && hour.precipitation() != null && hour.precipitation() >= HOUR;
    }

    /**
     * The first hourly step from now over the horizon that brings at least {@link #HOUR} millimetres, in the
     * order the forecast has them; empty when there is none (6-hour steps are never read as an hour).
     */
    public static Optional<ForecastHour> firstHeavyHour(Forecast forecast, Instant now, Duration horizon) {
        return forecast.hours().stream()
                .filter(h -> !h.time().isBefore(now.minus(Duration.ofHours(1))) && h.time().isBefore(now.plus(horizon)))
                .filter(HeavyRainRule::heavyHour)
                .min(Comparator.comparing(ForecastHour::time));
    }

    /** The first day from today on whose forecast total is heavy, if any. */
    public static Optional<DailyForecast> firstHeavyDay(Forecast forecast, LocalDate today, int days, ZoneId zone) {
        return DailyForecast.of(forecast, zone, today, days).stream().filter(d -> heavyDay(d.precipitation()))
                .findFirst();
    }

    private HeavyRainRule() {
    }
}
