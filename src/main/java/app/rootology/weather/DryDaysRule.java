package app.rootology.weather;

import java.util.List;

/**
 * dry-days-v1: the coming three days bring less than 1 mm of rain in all and at least one of them is warm
 * (maximum at or above 25 degrees C): a reminder to water, for the areas that dry out first. It judges the
 * weather only; which areas it concerns (pots, planters and raised beds, not open ground) is the alert's
 * business. Thresholds approved by the owner on 2026-11-18.
 */
public final class DryDaysRule {

    public static final String ID = "dry-days-v1";
    public static final int DAYS = 3;
    /** The rain over the whole window below which the days count as dry, in mm. */
    public static final double WINDOW_RAIN = 1.0;
    /** A day this warm evaporates enough to ask for water. */
    public static final double WARM = 25.0;

    /**
     * Whether these days are dry and warm. Fewer than {@link #DAYS} days is not enough to tell: never a guess.
     */
    public static boolean dryAndWarm(List<DailyForecast> days) {
        if (days.size() < DAYS) {
            return false;
        }
        List<DailyForecast> window = days.subList(0, DAYS);
        double rain = window.stream().mapToDouble(DailyForecast::precipitation).sum();
        return rain < WINDOW_RAIN && window.stream().anyMatch(d -> d.maximum() >= WARM);
    }

    private DryDaysRule() {
    }
}
