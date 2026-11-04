package io.github.vincenzofiore.rootology.weather;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** frost-risk-v1 and smith-period-v1 on hand-made forecasts. */
class RulesTest {

    static final ZoneId ROME = ZoneId.of("Europe/Rome");
    static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    /** Hourly steps from start, with a temperature and humidity per hour from the functions. */
    static Forecast hourly(Instant start, int hours, java.util.function.IntToDoubleFunction temperature,
                           java.util.function.IntToDoubleFunction humidity) {
        List<ForecastHour> steps = new ArrayList<>();
        for (int i = 0; i < hours; i++) {
            steps.add(new ForecastHour(start.plus(i, ChronoUnit.HOURS), temperature.applyAsDouble(i),
                    humidity.applyAsDouble(i), 0.0, 1));
        }
        return new Forecast(steps, NOW, false);
    }

    @Test
    void frostLooksAtTheComingWeekOnly() {
        // -6 degrees two days ago (past) and in 8 days (beyond the horizon); 0.5 degrees in 3 days.
        Forecast forecast = hourly(NOW.minus(2, ChronoUnit.DAYS), 24 * 11,
                i -> i == 0 || i == 24 * 10 ? -6.0 : i == 24 * 5 ? 0.5 : 8.0, i -> 80.0);

        Optional<ForecastHour> coldest = FrostRule.coldest(forecast, NOW);

        assertThat(coldest).map(ForecastHour::airTemperature).contains(0.5);
        assertThat(FrostRule.tooCold(0.5, 0.0)).isTrue();   // within the 1 degree margin
        assertThat(FrostRule.tooCold(0.5, -5.0)).isFalse(); // garlic stands it
    }

    @Test
    void twoWarmHumidDaysMakeASmithPeriod() {
        Instant midnight = LocalDate.of(2026, 11, 3).atStartOfDay(ROME).toInstant();
        Forecast forecast = hourly(midnight, 48, i -> 12.0, i -> i % 24 < 12 ? 95.0 : 70.0);

        assertThat(SmithPeriodRule.firstPeriod(forecast, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 7), ROME))
                .contains(LocalDate.of(2026, 11, 4));
    }

    @Test
    void aColdNightOrTooFewHumidHoursBreakIt() {
        Instant midnight = LocalDate.of(2026, 11, 3).atStartOfDay(ROME).toInstant();
        Forecast cold = hourly(midnight, 48, i -> i == 30 ? 9.5 : 12.0, i -> 95.0);
        Forecast dry = hourly(midnight, 48, i -> 12.0, i -> i % 24 < 10 ? 95.0 : 70.0);
        Forecast unknownHumidity = new Forecast(hourly(midnight, 48, i -> 12.0, i -> 95.0).hours().stream()
                .map(h -> new ForecastHour(h.time(), h.airTemperature(), null, null, 1)).toList(), NOW, false);

        LocalDate from = LocalDate.of(2026, 11, 2);
        LocalDate to = LocalDate.of(2026, 11, 7);
        assertThat(SmithPeriodRule.firstPeriod(cold, from, to, ROME)).isEmpty();
        assertThat(SmithPeriodRule.firstPeriod(dry, from, to, ROME)).isEmpty();
        assertThat(SmithPeriodRule.firstPeriod(unknownHumidity, from, to, ROME)).isEmpty();
    }
}
