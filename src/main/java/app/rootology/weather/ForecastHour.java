package app.rootology.weather;

import java.time.Instant;

/**
 * One step of a forecast: air temperature (degrees C) and relative humidity (%) at its start, the
 * precipitation (mm) over the step, the step's length in hours (1 near term, 6 further ahead), and MET Norway's
 * weather symbol for the step (for example partlycloudy_day; null when not given).
 */
public record ForecastHour(Instant time, double airTemperature, Double relativeHumidity, Double precipitation,
                           int stepHours, String symbol) {

    public ForecastHour(Instant time, double airTemperature, Double relativeHumidity, Double precipitation,
                        int stepHours) {
        this(time, airTemperature, relativeHumidity, precipitation, stepHours, null);
    }
}
