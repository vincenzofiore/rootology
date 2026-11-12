package app.rootology.alerts;

import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GrowingGarden;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.PlotKind;
import app.rootology.notifications.NotificationService;
import app.rootology.weather.Forecast;
import app.rootology.weather.ForecastHour;
import app.rootology.weather.FrostRule;
import app.rootology.weather.SmithPeriodRule;
import app.rootology.weather.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The morning check of #37. An alert names the garden, the plots and the crops at risk, the rule and the
 * forecast; crops whose frost tolerance is unknown are named as such, never guessed. Each event has a key
 * (garden, rule, day), so the same forecast frost is notified once even if seen on several mornings.
 */
@Component
public class WeatherAlerts {

    /** How far ahead the alerts look: closer than the 7 days of the sowing answers, so they are more reliable. */
    static final Duration HORIZON = Duration.ofDays(3);
    /** Below this, crops with unknown frost tolerance are worth naming. */
    static final double UNKNOWN_TOLERANCE_ALERT = 0.0;

    private static final Logger log = LoggerFactory.getLogger(WeatherAlerts.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN);

    private final GardenService gardens;
    private final WeatherService weather;
    private final KnowledgeGraph knowledge;
    private final CropCatalog crops;
    private final NotificationService notifications;
    private final Clock clock;

    WeatherAlerts(GardenService gardens, WeatherService weather, KnowledgeGraph knowledge, CropCatalog crops,
                  NotificationService notifications, Clock clock) {
        this.gardens = gardens;
        this.weather = weather;
        this.knowledge = knowledge;
        this.crops = crops;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "${rootology.alerts.cron}", zone = "Europe/Rome")
    void everyMorning() {
        int alerts = check();
        log.info("Weather alerts: {} new", alerts);
    }

    /** The same check on demand, for an admin trying it out (Admin view); still never repeats an alert. */
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public int checkNow() {
        return check();
    }

    /** Checks every garden with coordinates; returns the number of new alerts (one per garden and event). */
    public int check() {
        LocalDate today = LocalDate.now(clock);
        int alerts = 0;
        for (GrowingGarden garden : gardens.locatedGardensGrowing(today)) {
            if (garden.growing().isEmpty()) {
                continue;
            }
            Optional<Forecast> forecast = weather.around(garden.garden().latitude(), garden.garden().longitude());
            if (forecast.isEmpty()) {
                continue; // no weather today: no alert, never a guess
            }
            alerts += frost(garden, forecast.get()) + blight(garden, forecast.get(), today);
        }
        return alerts;
    }

    private int frost(GrowingGarden garden, Forecast forecast) {
        Optional<ForecastHour> coldest = FrostRule.coldest(forecast, clock.instant(), HORIZON);
        if (coldest.isEmpty()) {
            return 0;
        }
        double minimum = coldest.get().airTemperature();
        Map<String, Double> killing = knowledge.killingTemperatures();
        List<String> atRisk = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        boolean greenhouse = false;
        for (PlantingSummary p : garden.growing()) {
            Double kill = killing.get(p.cropId());
            if (kill == null) {
                unknown.add(name(p) + " (" + p.plotName() + ")");
            } else if (FrostRule.tooCold(minimum, kill)) {
                atRisk.add(name(p) + " (" + p.plotName() + ")");
            } else {
                continue;
            }
            greenhouse |= p.plotKind() == PlotKind.GREENHOUSE;
        }
        boolean unknownWorthIt = minimum <= UNKNOWN_TOLERANCE_ALERT && !unknown.isEmpty();
        // No source quantifies a greenhouse's protection: its crops are checked on the outdoor minimum (#85).
        greenhouse &= !atRisk.isEmpty() || unknownWorthIt;
        if (atRisk.isEmpty() && !unknownWorthIt) {
            return 0;
        }
        LocalDate day = LocalDate.ofInstant(coldest.get().time(), clock.getZone());
        String body = String.format(Locale.ITALIAN, "Minima prevista %.1f °C a %s (MET Norway, %s).", minimum,
                garden.garden().name(), FrostRule.ID)
                + (atRisk.isEmpty() ? "" : " A rischio: " + String.join(", ", atRisk) + ".")
                + (unknownWorthIt ? " Tolleranza al gelo non nota: " + String.join(", ", unknown) + "." : "")
                + (greenhouse ? " In serra o tunnel il rischio è minore: la minima è quella all’aperto." : "");
        int notified = notifications.weatherAlert(garden.spaceId(), garden.garden().id(),
                "frost:" + garden.garden().id() + ":" + day, "Rischio gelo " + DAY.format(day), body);
        return notified > 0 ? 1 : 0;
    }

    private int blight(GrowingGarden garden, Forecast forecast, LocalDate today) {
        List<String> hosts = garden.growing().stream().filter(p -> SmithPeriodRule.HOSTS.contains(p.cropId()))
                .map(p -> name(p) + " (" + p.plotName() + ")").distinct().toList();
        if (hosts.isEmpty()) {
            return 0;
        }
        Optional<LocalDate> period = SmithPeriodRule.firstPeriod(forecast, today, today.plusDays(3), clock.getZone());
        if (period.isEmpty()) {
            return 0;
        }
        String body = "Periodo caldo e umido fino a " + DAY.format(period.get()) + " a " + garden.garden().name()
                + " (MET Norway, " + SmithPeriodRule.ID + "): favorevole alla peronospora su " + String.join(", ", hosts)
                + ".";
        int notified = notifications.weatherAlert(garden.spaceId(), garden.garden().id(),
                "blight:" + garden.garden().id() + ":" + period.get(), "Rischio peronospora " + DAY.format(period.get()),
                body);
        return notified > 0 ? 1 : 0;
    }

    private String name(PlantingSummary p) {
        return crops.byId(p.cropId()).map(c -> c.italianName()).orElse(p.cropName());
    }
}
