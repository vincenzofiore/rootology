package app.rootology.weather;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One day of the weather card (#31): minimum and maximum air temperature, total precipitation and the symbol
 * of the step nearest midday (MET Norway's symbol code, or null).
 */
public record DailyForecast(LocalDate day, double minimum, double maximum, double precipitation, String symbol) {

    /** The days of a forecast from a date on, at most `days` of them, in the garden's time zone. */
    public static List<DailyForecast> of(Forecast forecast, ZoneId zone, LocalDate from, int days) {
        Map<LocalDate, List<ForecastHour>> byDay = new TreeMap<>();
        for (ForecastHour h : forecast.hours()) {
            LocalDate day = LocalDate.ofInstant(h.time(), zone);
            if (!day.isBefore(from)) {
                byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(h);
            }
        }
        List<DailyForecast> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ForecastHour>> e : byDay.entrySet()) {
            if (result.size() == days) {
                break;
            }
            List<ForecastHour> hours = e.getValue();
            double min = hours.stream().mapToDouble(ForecastHour::airTemperature).min().orElseThrow();
            double max = hours.stream().mapToDouble(ForecastHour::airTemperature).max().orElseThrow();
            double rain = hours.stream().mapToDouble(h -> h.precipitation() == null ? 0 : h.precipitation()).sum();
            String symbol = hours.stream().filter(h -> h.symbol() != null)
                    .min(Comparator.comparingLong(h -> Math.abs(LocalTime.ofInstant(h.time(), zone).toSecondOfDay()
                            - LocalTime.NOON.toSecondOfDay())))
                    .map(ForecastHour::symbol).orElse(null);
            result.add(new DailyForecast(e.getKey(), min, max, rain, symbol));
        }
        return result;
    }
}
