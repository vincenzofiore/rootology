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
        assertThat(AlertSeverity.of("heat:12:2026-11-12")).isEqualTo(AlertSeverity.HEAT);
        assertThat(AlertSeverity.of("rain:12:2026-11-12")).isEqualTo(AlertSeverity.RAIN);
        assertThat(AlertSeverity.of("wind:12:2026-11-12")).isEqualTo(AlertSeverity.WIND);
        assertThat(AlertSeverity.of("dry:12:2026-11-12")).isEqualTo(AlertSeverity.DRY);
        // A rule the home does not know yet is still shown, in the neutral colour, never guessed.
        assertThat(AlertSeverity.of("hail:12:2026-11-12")).isEqualTo(AlertSeverity.OTHER);
        assertThat(AlertSeverity.of(null)).isEqualTo(AlertSeverity.OTHER);
    }

    @Test
    void anOfficialWarningIsColouredByItsLevelAndDrawnByItsType() {
        // MeteoAlarm's levels: 2 yellow, 3 orange, 4 red; green and a level it does not state stay neutral.
        assertThat(OfficialWarningLevel.of(2).className()).isEqualTo("rootology-official-yellow");
        assertThat(OfficialWarningLevel.of(3).className()).isEqualTo("rootology-official-orange");
        assertThat(OfficialWarningLevel.of(4).className()).isEqualTo("rootology-official-red");
        assertThat(OfficialWarningLevel.of(1)).isEqualTo(OfficialWarningLevel.GREEN);
        assertThat(OfficialWarningLevel.of(1).className()).isEqualTo("rootology-official-neutral");
        assertThat(OfficialWarningLevel.of(0)).isEqualTo(OfficialWarningLevel.OTHER);
        assertThat(OfficialWarningLevel.of(9)).isEqualTo(OfficialWarningLevel.OTHER);

        // Every awareness_type of the feed has its icon; 11 and anything else keep the generic one.
        assertThat(OfficialWarningType.of(1)).isEqualTo(OfficialWarningType.WIND);
        assertThat(OfficialWarningType.of(10)).isEqualTo(OfficialWarningType.RAIN);
        assertThat(OfficialWarningType.of(12)).isEqualTo(OfficialWarningType.FLOODING);
        assertThat(OfficialWarningType.of(13)).isEqualTo(OfficialWarningType.RAIN_FLOOD);
        assertThat(OfficialWarningType.of(11)).isEqualTo(OfficialWarningType.OTHER);
        assertThat(OfficialWarningType.of(0)).isEqualTo(OfficialWarningType.OTHER);
        assertThat(OfficialWarningType.of(1).label()).isEqualTo("warning.type.wind");
    }

    @Test
    void onlyAFrostDayOfTheForecastIsMarkedAndTheFirstOne() {
        assertThat(WeatherCard.firstFrost(List.of(day(12, 5), day(13, 0), day(14, -2))))
                .contains(day(13, 0));
        assertThat(WeatherCard.firstFrost(List.of(day(12, 5), day(13, 4)))).isEmpty();
        assertThat(WeatherCard.firstFrost(List.of())).isEmpty();
    }

    @Test
    void theWeatherOfNowIsTheNearestHourWithinAnHour() {
        java.time.Instant now = java.time.Instant.parse("2026-11-17T14:20:00Z");
        app.rootology.weather.ForecastHour two = hour("2026-11-17T14:00:00Z");
        app.rootology.weather.ForecastHour three = hour("2026-11-17T15:00:00Z");

        assertThat(WeatherCard.now(List.of(three, two), now)).contains(two);
        assertThat(WeatherCard.now(List.of(hour("2026-11-17T18:00:00Z")), now))
                .as("a six-hour step far from now is not the weather of now").isEmpty();
    }

    private static app.rootology.weather.ForecastHour hour(String at) {
        return new app.rootology.weather.ForecastHour(java.time.Instant.parse(at), 18, 70.0, 0.0, 1, "cloudy");
    }
}
