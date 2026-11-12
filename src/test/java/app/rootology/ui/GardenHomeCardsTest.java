package app.rootology.ui;

import app.rootology.weather.DailyForecast;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The cards of the garden home (#84) and the frost marker of the plots page: only their rules. */
class GardenHomeCardsTest {

    private static DailyForecast day(int dayOfMonth, double minimum) {
        return new DailyForecast(LocalDate.of(2026, 11, dayOfMonth), minimum, minimum + 8, 0, "clearsky_day");
    }

    @Test
    void aWeatherAlertIsColouredByTheRuleThatSentIt() {
        assertThat(AlertSeverity.of("frost:12:2026-11-12")).isEqualTo(AlertSeverity.FROST);
        assertThat(AlertSeverity.of("blight:12:2026-11-12")).isEqualTo(AlertSeverity.BLIGHT);
        // A rule the home does not know yet is still shown, in the neutral colour, never guessed.
        assertThat(AlertSeverity.of("rain:12:2026-11-12")).isEqualTo(AlertSeverity.OTHER);
        assertThat(AlertSeverity.of(null)).isEqualTo(AlertSeverity.OTHER);
    }

    @Test
    void onlyAFrostDayOfTheForecastIsMarkedAndTheFirstOne() {
        assertThat(WeatherCard.firstFrost(List.of(day(12, 5), day(13, 0), day(14, -2))))
                .contains(day(13, 0));
        assertThat(WeatherCard.firstFrost(List.of(day(12, 5), day(13, 4)))).isEmpty();
        assertThat(WeatherCard.firstFrost(List.of())).isEmpty();
    }
}
