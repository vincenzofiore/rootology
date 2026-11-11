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
        spaces.signedIn(owner);
        spaces.signedIn(guest);
        long space = spaces.spacesOf(owner).getFirst().id();
        spaces.addMember(space, owner, guest, Role.GUEST);
        var garden = gardens.saveGarden(space, owner, null, "Orto di " + owner, null, new BigDecimal(lat),
                new BigDecimal(lon), "IT");
        var plot = gardens.savePlot(space, owner, garden.id(), null, "Aiuola A", PlotKind.GROUND, Soil.LOAMY, null);
        for (String crop : crops) {
            gardens.plant(space, owner, plot.id(), crop, PlantingMethod.SOWN, LocalDate.of(2026, 10, 1), null);
        }
        return space;
    }

    private static Optional<Forecast> steady(double temperature, double humidity) {
        List<ForecastHour> hours = new ArrayList<>();
        Instant start = NOW.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        for (int i = 0; i < 24 * 6; i++) {
            hours.add(new ForecastHour(start.plus(i, ChronoUnit.HOURS), temperature, humidity, 0.0, 1));
        }
        return Optional.of(new Forecast(hours, NOW, false));
    }

    private List<NotificationView> weatherOf(String login) {
        return notifications.of(login, 20).stream().filter(NotificationView::weather).toList();
    }

    @Test
    void frostWarnsEveryMemberOnceNamingCropsAtRiskAndUnknownOnes() {
        when(weather.around(any(), any())).thenReturn(Optional.empty());
        garden("fabio", "gaia", "45.46", "9.19", "lettuce", "garlic", "tomato");
        when(weather.around(eq(new BigDecimal("45.46")), eq(new BigDecimal("9.19")))).thenReturn(steady(-2.0, 80.0));

        alerts.check();

        assertThat(weatherOf("fabio")).singleElement().satisfies(n -> {
            assertThat(n.title()).startsWith("Rischio gelo");
            assertThat(n.body()).contains("-2,0 °C").contains("A rischio: Lattuga (Aiuola A)")
                    .contains("Tolleranza al gelo non nota: Pomodoro (Aiuola A)").doesNotContain("Aglio");
        });
        assertThat(weatherOf("gaia")).as("a guest is told too").hasSize(1);

        alerts.check();
        assertThat(weatherOf("fabio")).as("the same frost, never twice").hasSize(1);
    }

    @Test
    void warmHumidDaysWarnOfLateBlightOnTomatoAndPotato() {
        when(weather.around(any(), any())).thenReturn(Optional.empty());
        garden("ilaria", "luca", "41.90", "12.50", "tomato", "lettuce");
        when(weather.around(eq(new BigDecimal("41.90")), eq(new BigDecimal("12.50")))).thenReturn(steady(14.0, 95.0));

        alerts.check();

        assertThat(weatherOf("ilaria")).singleElement().satisfies(n -> {
            assertThat(n.title()).startsWith("Rischio peronospora");
            assertThat(n.body()).contains("Pomodoro (Aiuola A)").contains("smith-period-v1").doesNotContain("Lattuga");
        });
    }

    @Test
    void noForecastOrMildWeatherMeansNoAlert() {
        when(weather.around(any(), any())).thenReturn(Optional.empty());
        garden("marco", "nadia", "40.85", "14.27", "tomato");
        alerts.check();
        assertThat(weatherOf("marco")).isEmpty();

        when(weather.around(eq(new BigDecimal("40.85")), eq(new BigDecimal("14.27")))).thenReturn(steady(15.0, 60.0));
        alerts.check();
        assertThat(weatherOf("marco")).isEmpty();
    }

    @Test
    void weatherEmailsFollowTheirOwnPreference() {
        when(weather.around(any(), any())).thenReturn(Optional.empty());
        garden("olga", "pino", "43.77", "11.25", "lettuce");
        notifications.save("olga", new NotificationPreferences("olga@example.org", true, true, true, true, null, null));
        notifications.save("pino", new NotificationPreferences("pino@example.org", true, false, true, true, null, null));
        when(weather.around(eq(new BigDecimal("43.77")), eq(new BigDecimal("11.25")))).thenReturn(steady(-3.0, 80.0));

        alerts.check();

        assertThat(notifications.digest()).as("olga only: pino does not want weather emails").isEqualTo(1);
    }
}
