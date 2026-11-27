package app.rootology.alerts;

import app.rootology.TestcontainersConfiguration;
import app.rootology.garden.GardenService;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.notifications.NotificationService;
import app.rootology.notifications.NotificationView;
import app.rootology.officialwarnings.OfficialWarnings;
import app.rootology.spaces.SpaceService;
import app.rootology.weather.Forecast;
import app.rootology.weather.ForecastHour;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.when;

/**
 * The morning alerts follow each garden's own zone (#158): the hourly tick checks the gardens whose local time
 * is 7, on their own day, and an admin's check takes them all whatever the hour.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"rootology.alerts.cron=0 0 0 1 1 *",
        "rootology.notifications.digest-every=PT1H", "rootology.notifications.group-window=PT0S"})
class WeatherAlertsZoneTest {

    static final ZoneId ITALY = ZoneId.of("Europe/Rome");
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    static Instant now = Instant.parse("2026-11-04T06:00:00Z");

    @TestBean
    Clock clock;

    static Clock clock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ITALY;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
    }

    @MockitoBean
    WeatherService weather;

    @MockitoBean
    OfficialWarnings official;

    @Autowired
    WeatherAlerts alerts;

    @Autowired
    NotificationService notifications;

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @BeforeEach
    void aFixedHour() {
        now = Instant.parse("2026-11-04T06:00:00Z");
    }

    /** A garden of its own space, with a stored zone, growing a tomato whose frost tolerance is unknown. */
    private long garden(String owner, String country, String lat, String lon, String zone) {
        spaces.signedIn(owner);
        long space = app.rootology.TestSpaces.personal(spaces, owner);
        var garden = gardens.saveGarden(space, owner, null, "Orto di " + owner, null, new BigDecimal(lat),
                new BigDecimal(lon), country, null, zone);
        var plot = gardens.savePlot(space, owner, garden.id(), null, "Aiuola A", PlotKind.GROUND, Soil.LOAMY, null);
        gardens.plant(space, owner, plot.id(), "tomato", PlantingMethod.SOWN, LocalDate.of(2026, 10, 1), null);
        return garden.id();
    }

    /** Six days of hourly steps from yesterday, all at this temperature: a certain frost every morning. */
    private Optional<Forecast> steady(double temperature) {
        List<ForecastHour> hours = new ArrayList<>();
        Instant start = now.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        for (int i = 0; i < 24 * 6; i++) {
            hours.add(new ForecastHour(start.plus(i, ChronoUnit.HOURS), temperature, 80.0, 0.0, 1));
        }
        return Optional.of(new Forecast(hours, now, false));
    }

    private List<NotificationView> alertsOf(String login) {
        return notifications.of(login, 20).stream().filter(NotificationView::weather).toList();
    }

    @Test
    void aGardenIsCheckedAtSevenWhereItIsNotAtSevenInItaly() {
        now = Instant.parse("2026-11-04T12:00:00Z"); // 13:00 in Rome, 07:00 in New York
        when(weather.around(any(), any(), any())).thenReturn(steady(-10.0));
        garden("zone-ny-owner", "US", "40.71", "-74.00", "America/New_York");
        garden("zone-rome-owner", "IT", "41.90", "12.50", "Europe/Rome");

        alerts.everyHour();

        assertThat(alertsOf("zone-ny-owner")).as("7:00 there").hasSize(1);
        assertThat(alertsOf("zone-rome-owner")).as("13:00 there").isEmpty();
    }

    @Test
    void anItalianGardenIsCheckedWhenItIsSevenInItaly() {
        now = Instant.parse("2026-11-03T06:00:00Z"); // 07:00 in Rome, 01:00 in New York
        when(weather.around(any(), any(), any())).thenReturn(steady(-10.0));
        garden("zone-ny-early", "US", "40.71", "-74.00", "America/New_York");
        garden("zone-rome-morning", "IT", "41.90", "12.50", "Europe/Rome");

        alerts.everyHour();

        assertThat(alertsOf("zone-rome-morning")).as("7:00 there").hasSize(1);
        assertThat(alertsOf("zone-ny-early")).as("1:00 there").isEmpty();
    }

    @Test
    void anAdminsCheckTakesEveryGardenWhateverTheHour() {
        now = Instant.parse("2026-11-04T12:00:00Z"); // 13:00 in Rome, 07:00 in New York
        when(weather.around(any(), any(), any())).thenReturn(steady(-10.0));
        garden("zone-ny-admin", "US", "40.71", "-74.00", "America/New_York");
        garden("zone-rome-admin", "IT", "41.90", "12.50", "Europe/Rome");

        assertThat(alerts.check()).isGreaterThanOrEqualTo(2);

        assertThat(alertsOf("zone-ny-admin")).hasSize(1);
        assertThat(alertsOf("zone-rome-admin")).hasSize(1);
    }

    @Test
    void theDayOfTheAlertIsTheGardensOwnDay() {
        // 01:30 on 1 December in Rome, still 19:30 on 30 November in New York.
        now = Instant.parse("2026-12-01T00:30:00Z");
        when(weather.around(any(), any(), any())).thenReturn(theColdestHourAt(now.truncatedTo(ChronoUnit.HOURS)));
        garden("zone-ny-day", "US", "40.71", "-74.00", "America/New_York");
        garden("zone-rome-day", "IT", "41.90", "12.50", "Europe/Rome");

        alerts.check();

        assertThat(alertsOf("zone-rome-day")).singleElement()
                .satisfies(n -> assertThat(n.title()).as("1 December in Rome").contains("1 dicembre"));
        assertThat(alertsOf("zone-ny-day")).singleElement()
                .satisfies(n -> assertThat(n.title()).as("still 30 November there").contains("30 novembre"));
    }

    /** A forecast warm but for one cold hour, so the frost alert always names that hour's day. */
    private Optional<Forecast> theColdestHourAt(Instant cold) {
        List<ForecastHour> hours = new ArrayList<>();
        Instant start = now.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        for (int i = 0; i < 24 * 6; i++) {
            Instant at = start.plus(i, ChronoUnit.HOURS);
            hours.add(new ForecastHour(at, at.equals(cold) ? -10.0 : 5.0, 80.0, 0.0, 1));
        }
        return Optional.of(new Forecast(hours, now, false));
    }
}
