package io.github.vincenzofiore.rootology.weather;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * smith-period-v1: a "Smith period" favours late blight (Phytophthora infestans) on potato and tomato:
 * two consecutive days, each with a minimum air temperature of at least 10 degrees C and at least 11
 * hours of relative humidity at or above 90% (L. P. Smith, 1956, used by UK blight warnings until the
 * Hutton criteria of 2017). Days are counted in the garden's time zone, each forecast step counting for
 * its length; days without humidity values do not count.
 */
public final class SmithPeriodRule {

    public static final String ID = "smith-period-v1";
    public static final String LATE_BLIGHT = "PHYTIN";
    public static final List<String> HOSTS = List.of("potato", "tomato");
    static final double MIN_TEMPERATURE = 10.0;
    static final double HUMIDITY = 90.0;
    static final int HUMID_HOURS = 11;

    /** The second day of the first Smith period between from and to (inclusive), if any. */
    public static Optional<LocalDate> firstPeriod(Forecast forecast, LocalDate from, LocalDate to, ZoneId zone) {
        Map<LocalDate, double[]> days = new TreeMap<>(); // day -> {minimum temperature, humid hours, humidity known}
        for (ForecastHour h : forecast.hours()) {
            LocalDate day = LocalDate.ofInstant(h.time(), zone);
            double[] d = days.computeIfAbsent(day, k -> new double[] {Double.MAX_VALUE, 0, 0});
            d[0] = Math.min(d[0], h.airTemperature());
            if (h.relativeHumidity() != null) {
                d[2] = 1;
                if (h.relativeHumidity() >= HUMIDITY) {
                    d[1] += h.stepHours();
                }
            }
        }
        for (LocalDate day = from.plusDays(1); !day.isAfter(to); day = day.plusDays(1)) {
            if (qualifies(days.get(day.minusDays(1))) && qualifies(days.get(day))) {
                return Optional.of(day);
            }
        }
        return Optional.empty();
    }

    private static boolean qualifies(double[] day) {
        return day != null && day[2] == 1 && day[0] >= MIN_TEMPERATURE && day[1] >= HUMID_HOURS;
    }

    private SmithPeriodRule() {
    }
}
