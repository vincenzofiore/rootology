package app.rootology.weather;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** heat-v1, heavy-rain-v1, wind-v1 and dry-days-v1 (#137) on hand-made forecasts, at the threshold edges. */
class AlertRulesTest {

    static final ZoneId ROME = ZoneId.of("Europe/Rome");
    static final Instant NOW = Instant.parse("2026-08-11T06:00:00Z");
    static final Duration HORIZON = Duration.ofDays(3);

    /** Hourly steps from start; each gets the temperature, precipitation and wind speed of its index. */
    static Forecast hours(Instant start, int count, java.util.function.IntToDoubleFunction precipitation,
                          java.util.function.IntFunction<Double> wind) {
        List<ForecastHour> steps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            steps.add(new ForecastHour(start.plus(i, ChronoUnit.HOURS), 20.0, 60.0, precipitation.applyAsDouble(i), 1,
                    null, wind.apply(i)));
        }
        return new Forecast(steps, NOW, false);
    }

    @Test
    void heatIsJudgedAgainstTheCropsAbsoluteMaximum() {
        assertThat(HeatRule.tooHot(35.0, 35.0)).as("at the limit counts").isTrue();
        assertThat(HeatRule.tooHot(34.9, 35.0)).isFalse();
    }

    @Test
    void thirtyMillimetresInADayAreHeavyAndJustUnderAreNot() {
        LocalDate today = LocalDate.of(2026, 8, 11);
        Instant midnight = today.atStartOfDay(ROME).toInstant();
        // One day at exactly 30 mm, the others dry.
        Forecast heavy = hours(midnight, 48, i -> i < 24 ? 1.25 : 0.0, i -> null);
        Forecast under = hours(midnight, 48, i -> i < 24 ? 1.249 : 0.0, i -> null);

        assertThat(HeavyRainRule.firstHeavyDay(heavy, today, 3, ROME)).get()
                .extracting(DailyForecast::day).isEqualTo(today);
        assertThat(HeavyRainRule.firstHeavyDay(under, today, 3, ROME)).isEmpty();
    }

    @Test
    void tenMillimetresCountInAnHourButNeverInASixHourStep() {
        Instant midnight = LocalDate.of(2026, 8, 11).atStartOfDay(ROME).toInstant();
        Forecast hourly = hours(midnight, 24, i -> i == 5 ? 10.0 : 0.0, i -> null);
        Forecast justUnder = hours(midnight, 24, i -> i == 5 ? 9.9 : 0.0, i -> null);
        // Further ahead MET Norway gives 6-hour steps: their total says nothing about an hour.
        List<ForecastHour> sixHourly = List.of(
                new ForecastHour(midnight.plus(12, ChronoUnit.HOURS), 20.0, 60.0, 12.0, 6),
                new ForecastHour(midnight.plus(18, ChronoUnit.HOURS), 20.0, 60.0, 0.0, 6));

        assertThat(HeavyRainRule.firstHeavyHour(hourly, midnight, HORIZON)).get()
                .extracting(ForecastHour::precipitation).isEqualTo(10.0);
        assertThat(HeavyRainRule.firstHeavyHour(justUnder, midnight, HORIZON)).isEmpty();
        assertThat(HeavyRainRule.firstHeavyHour(new Forecast(sixHourly, NOW, false), midnight, HORIZON)).isEmpty();
    }

    @Test
    void elevenMetresPerSecondIsStrongWind() {
        Instant midnight = LocalDate.of(2026, 8, 11).atStartOfDay(ROME).toInstant();
        Forecast strong = hours(midnight, 24, i -> 0.0, i -> i == 8 ? 11.0 : 6.0);
        Forecast calm = hours(midnight, 24, i -> 0.0, i -> i == 8 ? 10.9 : 6.0);

        assertThat(WindRule.strongest(strong, midnight, HORIZON)).get()
                .extracting(ForecastHour::windSpeed).isEqualTo(11.0);
        assertThat(WindRule.strongest(calm, midnight, HORIZON).filter(h -> WindRule.strong(h.windSpeed()))).isEmpty();
        assertThat(WindRule.strong(null)).as("no wind speed, no judgement").isFalse();
    }

    @Test
    void threeDryWarmDaysAskForWaterButFewerOrCoolerDoNot() {
        LocalDate day = LocalDate.of(2026, 8, 11);
        List<DailyForecast> dry = List.of(day(day, 25.0, 0.4), day(day.plusDays(1), 24.0, 0.5),
                day(day.plusDays(2), 30.0, 0.0));
        List<DailyForecast> wet = List.of(day(day, 25.0, 0.6), day(day.plusDays(1), 24.0, 0.4),
                day(day.plusDays(2), 30.0, 0.0));
        List<DailyForecast> cool = List.of(day(day, 24.9, 0.0), day(day.plusDays(1), 24.0, 0.0),
                day(day.plusDays(2), 20.0, 0.0));

        assertThat(DryDaysRule.dryAndWarm(dry)).isTrue();
        assertThat(DryDaysRule.dryAndWarm(wet)).as("1 mm in all is not dry enough").isFalse();
        assertThat(DryDaysRule.dryAndWarm(cool)).as("no warm day").isFalse();
        assertThat(DryDaysRule.dryAndWarm(dry.subList(0, 2))).as("fewer than three days, no judgement").isFalse();
    }

    private static DailyForecast day(LocalDate day, double maximum, double precipitation) {
        return new DailyForecast(day, maximum - 10, maximum, precipitation, null);
    }
}
