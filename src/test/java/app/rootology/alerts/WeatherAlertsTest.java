package app.rootology.alerts;

import app.rootology.TestcontainersConfiguration;
import app.rootology.garden.GardenService;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.notifications.NotificationPreferences;
import app.rootology.notifications.NotificationService;
import app.rootology.notifications.NotificationView;
import app.rootology.spaces.Role;
import app.rootology.spaces.SpaceService;
import app.rootology.weather.Forecast;
import app.rootology.weather.ForecastHour;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Weather alerts (#37) on a fixed morning, 4 November 2026, with forecasts made up per garden. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"rootology.notifications.digest-every=PT1H", "rootology.notifications.group-window=PT0S"})
class WeatherAlertsTest {

    static final ZoneId ITALY = ZoneId.of("Europe/Rome");
    static final Instant NOW = Instant.parse("2026-11-04T06:00:00Z");
    static final Instant START = NOW.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

    @TestBean
    Clock clock;

    static Clock clock() {
        return Clock.fixed(NOW, ITALY);
    }

    @MockitoBean
    WeatherService weather;

    @Autowired
    WeatherAlerts alerts;

    @Autowired
    NotificationService notifications;

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    /** A space with an owner and a guest, and a garden at these coordinates growing these crops. */
    private long garden(String owner, String guest, String lat, String lon, String... crops) {
        return garden(PlotKind.GROUND, owner, guest, lat, lon, crops);
    }

    private long garden(PlotKind kind, String owner, String guest, String lat, String lon, String... crops) {
        spaces.signedIn(owner);
        spaces.signedIn(guest);
        long space = app.rootology.TestSpaces.personal(spaces, owner);
        spaces.addMember(space, owner, guest, Role.GUEST);
        var garden = gardens.saveGarden(space, owner, null, "Orto di " + owner, null, new BigDecimal(lat),
                new BigDecimal(lon), "IT");
        var plot = gardens.savePlot(space, owner, garden.id(), null, "Aiuola A", kind, Soil.LOAMY, null);
        for (String crop : crops) {
            gardens.plant(space, owner, plot.id(), crop, PlantingMethod.SOWN, LocalDate.of(2026, 10, 1), null);
        }
        return space;
    }

    private static Optional<Forecast> steady(double temperature, double humidity) {
        return series(i -> new ForecastHour(START.plus(i, ChronoUnit.HOURS), temperature, humidity, 0.0, 1));
    }

    /** Six days of hourly steps from yesterday, one per index, so a test can shape a single hour. */
    private static Optional<Forecast> series(java.util.function.IntFunction<ForecastHour> hour) {
        List<ForecastHour> hours = new ArrayList<>();
        for (int i = 0; i < 24 * 6; i++) {
            hours.add(hour.apply(i));
        }
        return Optional.of(new Forecast(hours, NOW, false));
    }

    private List<NotificationView> weatherOf(String login) {
        return notifications.of(login, 20).stream().filter(NotificationView::weather).toList();
    }

    @Test
    void frostWarnsEveryMemberOnceNamingCropsAtRiskAndUnknownOnes() {
        when(weather.around(any(), any(), any())).thenReturn(Optional.empty());
        garden("fabio", "gaia", "45.46", "9.19", "lettuce", "garlic", "tomato");
        when(weather.around(eq(new BigDecimal("45.46")), eq(new BigDecimal("9.19")), any()))
                .thenReturn(steady(-2.0, 80.0));

        alerts.check();

        assertThat(weatherOf("fabio")).singleElement().satisfies(n -> {
            assertThat(n.title()).startsWith("Rischio gelo");
            assertThat(n.body()).contains("-2,0 °C").contains("A rischio: Lattuga (Aiuola A)")
                    .contains("Tolleranza al gelo non nota: Pomodoro (Aiuola A)").doesNotContain("Aglio", "serra");
        });
        assertThat(weatherOf("gaia")).as("a guest is told too").hasSize(1);

        alerts.check();
        assertThat(weatherOf("fabio")).as("the same frost, never twice").hasSize(1);
    }

    @Test
    void frostInAGreenhouseSaysTheOutdoorMinimumWasUsed() {
        when(weather.around(any(), any(), any())).thenReturn(Optional.empty());
        garden(PlotKind.GREENHOUSE, "rita", "sara", "45.07", "7.69", "lettuce");
        when(weather.around(eq(new BigDecimal("45.07")), eq(new BigDecimal("7.69")), any()))
                .thenReturn(steady(-2.0, 80.0));

        alerts.check();

        assertThat(weatherOf("rita")).singleElement().satisfies(n -> assertThat(n.body())
                .contains("A rischio: Lattuga (Aiuola A)")
                .contains("In serra o tunnel il rischio è minore: la minima è quella all’aperto."));
    }

    @Test
    void warmHumidDaysWarnOfLateBlightOnTomatoAndPotato() {
        when(weather.around(any(), any(), any())).thenReturn(Optional.empty());
        garden("ilaria", "luca", "41.90", "12.50", "tomato", "lettuce");
        when(weather.around(eq(new BigDecimal("41.90")), eq(new BigDecimal("12.50")), any()))
                .thenReturn(steady(14.0, 95.0));

        alerts.check();

        assertThat(weatherOf("ilaria")).singleElement().satisfies(n -> {
            assertThat(n.title()).startsWith("Rischio peronospora");
            assertThat(n.body()).contains("Pomodoro (Aiuola A)").contains("smith-period-v1").doesNotContain("Lattuga");
        });
    }

    @Test
    void noForecastOrMildWeatherMeansNoAlert() {
        when(weather.around(any(), any(), any())).thenReturn(Optional.empty());
        garden("marco", "nadia", "40.85", "14.27", "tomato");
        alerts.check();
        assertThat(weatherOf("marco")).isEmpty();

        when(weather.around(eq(new BigDecimal("40.85")), eq(new BigDecimal("14.27")), any()))
                .thenReturn(steady(15.0, 60.0));
        alerts.check();
        assertThat(weatherOf("marco")).isEmpty();
    }

    @Test
    void heatWarnsNamingCropsBeyondTheirLimitAndThoseNotChecked() {
        when(weather.around(any(), any(), any())).thenReturn(Optional.empty());
        // Lattuga stands 30 degrees C (ECOCROP); the songino has no record, so it is named, never guessed.
        garden("paola", "quinto", "38.11", "13.35", "lettuce", "lambs-lettuce");
        when(weather.around(eq(new BigDecimal("38.11")), eq(new BigDecimal("13.35")), any()))
                .thenReturn(steady(31.0, 60.0));

        alerts.check();

        assertThat(weatherOf("paola")).singleElement().satisfies(n -> {
            assertThat(n.title()).startsWith("Rischio caldo estremo");
            assertThat(n.body()).contains("31,0 °C").contains("A rischio: Lattuga (Aiuola A)")
                    .contains("Limite di caldo non noto: Valerianella (songino) (Aiuola A)")
                    .contains("heat-v1").contains("FAO ECOCROP");
        });
    }

    @Test
    void thirtyMillimetresInADayWarnOfHeavyRain() {
        when(weather.around(any(), any(), any())).thenReturn(Optional.empty());
        garden("rosa", "tino", "37.51", "15.08", "lettuce");
        // 1.5 mm an hour through the whole of 5 November: 36 mm that day, well over the 30 of the rule.
        LocalDate wetDay = LocalDate.of(2026, 11, 5);
        when(weather.around(eq(new BigDecimal("37.51")), eq(new BigDecimal("15.08")), any()))
                .thenReturn(series(i -> {
            Instant time = START.plus(i, ChronoUnit.HOURS);
            boolean wet = LocalDate.ofInstant(time, ITALY).equals(wetDay);
            return new ForecastHour(time, 15.0, 70.0, wet ? 1.5 : 0.0, 1);
        }));

        alerts.check();

        assertThat(weatherOf("rosa")).singleElement().satisfies(n -> {
            assertThat(n.title()).startsWith("Pioggia intensa");
            assertThat(n.body()).contains("in un giorno").contains("heavy-rain-v1");
        });
    }

    @Test
    void strongWindIsWordedAsAPossibilityNeverACertainty() {
        when(weather.around(any(), any(), any())).thenReturn(Optional.empty());
        garden("sonia", "ugo", "44.06", "9.66", "lettuce");
        when(weather.around(eq(new BigDecimal("44.06")), eq(new BigDecimal("9.66")), any()))
                .thenReturn(series(i ->
                new ForecastHour(START.plus(i, ChronoUnit.HOURS), 15.0, 70.0, 0.0, 1, null,
                        i == 30 ? 12.0 : 5.0)));

        alerts.check();

        assertThat(weatherOf("sonia")).singleElement().satisfies(n -> {
            assertThat(n.title()).startsWith("Possibile vento forte");
            assertThat(n.body()).contains("12,0 m/s").contains("circa 43 km/h").contains("wind-v1")
                    .contains("Possibile vento forte");
        });
    }

    @Test
    void dryWarmDaysAskForWaterOnlyInPotsPlantersAndRaisedBeds() {
        when(weather.around(any(), any(), any())).thenReturn(Optional.empty());
        garden(PlotKind.POT, "teresa", "vera", "41.56", "14.66", "lettuce");
        garden(PlotKind.GROUND, "walter", "xenia", "41.57", "14.67", "lettuce");
        Optional<Forecast> dry = steady(26.0, 60.0);
        when(weather.around(eq(new BigDecimal("41.56")), eq(new BigDecimal("14.66")), any()))
                .thenReturn(dry);
        when(weather.around(eq(new BigDecimal("41.57")), eq(new BigDecimal("14.67")), any()))
                .thenReturn(dry);

        alerts.check();

        assertThat(weatherOf("teresa")).singleElement().satisfies(n -> {
            assertThat(n.title()).startsWith("Promemoria: annaffiare");
            assertThat(n.body()).contains("Lattuga (Aiuola A)").contains("dry-days-v1");
        });
        assertThat(weatherOf("walter")).as("open ground is not the reminder's business").isEmpty();
    }

    @Test
    void weatherEmailsFollowTheirOwnPreference() {
        when(weather.around(any(), any(), any())).thenReturn(Optional.empty());
        garden("olga", "pino", "43.77", "11.25", "lettuce");
        notifications.save("olga", new NotificationPreferences("olga@example.org", true, true, true, true, null, null));
        notifications.save("pino", new NotificationPreferences("pino@example.org", true, false, true, true, null, null));
        when(weather.around(eq(new BigDecimal("43.77")), eq(new BigDecimal("11.25")), any()))
                .thenReturn(steady(-3.0, 80.0));

        alerts.check();

        assertThat(notifications.digest()).as("olga only: pino does not want weather emails").isEqualTo(1);
    }
}
