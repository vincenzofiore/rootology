package io.github.vincenzofiore.rootology.weather;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyForecastTest {

    static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Test
    void daysInItalyWithMinimumMaximumRainAndTheMiddaySymbol() {
        Instant start = LocalDate.of(2026, 11, 4).atStartOfDay(ROME).toInstant();
        List<ForecastHour> hours = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            int hour = i % 24;
            hours.add(new ForecastHour(start.plus(i, ChronoUnit.HOURS), hour < 6 ? -1.0 + i / 24 : 8.0 + i / 24,
                    80.0, hour == 15 ? 2.5 : 0.0, 1, hour == 12 ? "rain" : "cloudy"));
        }

        List<DailyForecast> days = DailyForecast.of(new Forecast(hours, start, false), ROME, LocalDate.of(2026, 11, 4), 7);

        assertThat(days).hasSize(2);
        assertThat(days.getFirst()).isEqualTo(new DailyForecast(LocalDate.of(2026, 11, 4), -1.0, 8.0, 2.5, "rain"));
        assertThat(days.get(1).minimum()).isEqualTo(0.0);
        assertThat(DailyForecast.of(new Forecast(hours, start, false), ROME, LocalDate.of(2026, 11, 5), 7)).hasSize(1);
        assertThat(DailyForecast.of(new Forecast(hours, start, false), ROME, LocalDate.of(2026, 11, 4), 1)).hasSize(1);
    }
}
