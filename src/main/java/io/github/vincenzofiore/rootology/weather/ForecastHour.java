package io.github.vincenzofiore.rootology.weather;

import java.time.Instant;

/**
 * One step of a forecast: air temperature (degrees C) and relative humidity (%) at its start, the
 * precipitation (mm) over the step, and the step's length in hours (1 near term, 6 further ahead).
 */
public record ForecastHour(Instant time, double airTemperature, Double relativeHumidity, Double precipitation,
                           int stepHours) {
}
