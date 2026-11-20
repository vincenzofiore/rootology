package app.rootology.alerts;

import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GrowingGarden;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.PlotKind;
import app.rootology.notifications.NotificationService;
import app.rootology.weather.DailyForecast;
import app.rootology.weather.DryDaysRule;
import app.rootology.weather.Forecast;
import app.rootology.weather.ForecastHour;
import app.rootology.weather.FrostRule;
import app.rootology.weather.HeavyRainRule;
import app.rootology.weather.HeatRule;
import app.rootology.weather.SmithPeriodRule;
import app.rootology.weather.WeatherService;
import app.rootology.weather.WindRule;
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
 * Six rules over the same horizon: frost, late blight, heat, heavy rain, strong wind and dry days (#137).
 */
@Component
public class WeatherAlerts {

    /** How far ahead the alerts look: closer than the 7 days of the sowing answers, so they are more reliable. */
    static final Duration HORIZON = Duration.ofDays(3);
    /** Below this, crops with unknown frost tolerance are worth naming. */
    static final double UNKNOWN_TOLERANCE_ALERT = 0.0;
    /** The days the daily rules read (heat, heavy rain, dry days): the same horizon of 3 days. */
    static final int ALERT_DAYS = 3;

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
            Optional<Forecast> forecast = weather.around(garden.garden().latitude(),
                    garden.garden().longitude(), garden.garden().elevationM());
            if (forecast.isEmpty()) {
                continue; // no weather today: no alert, never a guess
            }
            Forecast f = forecast.get();
            alerts += frost(garden, f, today) + blight(garden, f, today) + heat(garden, f, today)
                    + heavyRain(garden, f, today) + wind(garden, f) + dryDays(garden, f, today);
        }
        return alerts;
    }

    private int frost(GrowingGarden garden, Forecast forecast, LocalDate today) {
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
            Double kill = tolerance(p, killing, today);
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
        return alerted(garden, "frost:" + garden.garden().id() + ":" + day, "Rischio gelo " + DAY.format(day), body);
    }

    /**
     * The temperature a planting stands: ECOCROP's killing temperature of young plants, or, for a perennial
     * planted at least a year ago, its own frost hardiness (#139), null when not known. ECOCROP's absolute
     * minimum is not used: it is where growth stops, not where the plant dies.
     */
    private Double tolerance(PlantingSummary p, Map<String, Double> killing, LocalDate today) {
        return established(p, today) ? crops.byId(p.cropId()).map(c -> c.frostHardiness()).orElse(null)
                : killing.get(p.cropId());
    }

    private boolean established(PlantingSummary p, LocalDate today) {
        return crops.byId(p.cropId()).map(c -> c.perennial()).orElse(false) && p.plantedOn() != null
                && !p.plantedOn().isAfter(today.minusYears(1));
    }

    private int blight(GrowingGarden garden, Forecast forecast, LocalDate today) {
        List<String> hosts = garden.growing().stream().filter(p -> SmithPeriodRule.HOSTS.contains(p.cropId()))
                .map(p -> name(p) + " (" + p.plotName() + ")").distinct().toList();
        if (hosts.isEmpty()) {
            return 0;
        }
        Optional<LocalDate> period = SmithPeriodRule.firstPeriod(forecast, today, today.plusDays(ALERT_DAYS),
                clock.getZone());
        if (period.isEmpty()) {
            return 0;
        }
        String body = "Periodo caldo e umido fino a " + DAY.format(period.get()) + " a " + garden.garden().name()
                + " (MET Norway, " + SmithPeriodRule.ID + "): favorevole alla peronospora su " + String.join(", ", hosts)
                + ".";
        return alerted(garden, "blight:" + garden.garden().id() + ":" + period.get(),
                "Rischio peronospora " + DAY.format(period.get()), body);
    }

    /**
     * heat-v1 (#137): the first day whose maximum reaches the absolute maximum of a crop growing here. Crops
     * without an ECOCROP value are named as not checked, as the frost rule does with the killing temperature.
     */
    private int heat(GrowingGarden garden, Forecast forecast, LocalDate today) {
        Map<String, Double> upper = knowledge.upperTemperatures();
        for (DailyForecast day : DailyForecast.of(forecast, clock.getZone(), today, ALERT_DAYS)) {
            List<String> atRisk = new ArrayList<>();
            List<String> unknown = new ArrayList<>();
            for (PlantingSummary p : garden.growing()) {
                Double limit = upper.get(p.cropId());
                if (limit == null) {
                    unknown.add(name(p) + " (" + p.plotName() + ")");
                } else if (HeatRule.tooHot(day.maximum(), limit)) {
                    atRisk.add(name(p) + " (" + p.plotName() + ")");
                }
            }
            if (atRisk.isEmpty()) {
                continue; // a hot day for no crop: nothing to say
            }
            String body = String.format(Locale.ITALIAN, "Massima prevista %.1f °C a %s (MET Norway, FAO ECOCROP, %s).",
                    day.maximum(), garden.garden().name(), HeatRule.ID)
                    + " A rischio: " + String.join(", ", atRisk) + "."
                    + (unknown.isEmpty() ? "" : " Limite di caldo non noto: " + String.join(", ", unknown) + ".");
            return alerted(garden, "heat:" + garden.garden().id() + ":" + day.day(),
                    "Rischio caldo estremo " + DAY.format(day.day()), body);
        }
        return 0;
    }

    /**
     * heavy-rain-v1 (#137): the first day totalling at least 30 mm, or the first hour bringing at least 10 mm
     * on a one-hour step; the earlier of the two, the day's total first when they fall on the same day.
     */
    private int heavyRain(GrowingGarden garden, Forecast forecast, LocalDate today) {
        Optional<DailyForecast> heavyDay = HeavyRainRule.firstHeavyDay(forecast, today, ALERT_DAYS, clock.getZone());
        Optional<ForecastHour> heavyHour = HeavyRainRule.firstHeavyHour(forecast, clock.instant(), HORIZON);
        LocalDate hourDay = heavyHour.map(h -> LocalDate.ofInstant(h.time(), clock.getZone())).orElse(null);
        boolean dayFirst = heavyDay.isPresent() && (hourDay == null || !hourDay.isBefore(heavyDay.get().day()));
        if (dayFirst) {
            DailyForecast day = heavyDay.get();
            String body = String.format(Locale.ITALIAN, "Previsti %.1f mm di pioggia in un giorno a %s (MET Norway, %s).",
                    day.precipitation(), garden.garden().name(), HeavyRainRule.ID);
            return alerted(garden, "rain:" + garden.garden().id() + ":" + day.day(),
                    "Pioggia intensa " + DAY.format(day.day()), body);
        }
        if (hourDay == null) {
            return 0;
        }
        String body = String.format(Locale.ITALIAN, "Previsti %.1f mm di pioggia in un’ora a %s (MET Norway, %s).",
                heavyHour.get().precipitation(), garden.garden().name(), HeavyRainRule.ID);
        return alerted(garden, "rain:" + garden.garden().id() + ":" + hourDay,
                "Pioggia intensa " + DAY.format(hourDay), body);
    }

    /**
     * wind-v1 (#137): the strongest step of the horizon reaches 11 m/s. MET Norway has no gusts and is
     * unreliable in complex terrain at 9 km: the wording says a possible strong wind, never a certainty.
     */
    private int wind(GrowingGarden garden, Forecast forecast) {
        Optional<ForecastHour> strongest = WindRule.strongest(forecast, clock.instant(), HORIZON)
                .filter(h -> WindRule.strong(h.windSpeed()));
        if (strongest.isEmpty()) {
            return 0;
        }
        double speed = strongest.get().windSpeed();
        LocalDate day = LocalDate.ofInstant(strongest.get().time(), clock.getZone());
        String body = String.format(Locale.ITALIAN,
                "Vento medio previsto %.1f m/s (circa %d km/h) a %s (MET Norway, %s). Possibile vento forte: in terreno "
                        + "complesso la previsione a 9 km può sbagliare.",
                speed, Math.round(speed * 3.6), garden.garden().name(), WindRule.ID);
        return alerted(garden, "wind:" + garden.garden().id() + ":" + day,
                "Possibile vento forte " + DAY.format(day), body);
    }

    /**
     * dry-days-v1 (#137): three days nearly without rain and one of them warm, as a reminder to water. Only the
     * areas that dry out first count: pots, planters and raised beds, never open ground or a greenhouse.
     */
    private int dryDays(GrowingGarden garden, Forecast forecast, LocalDate today) {
        List<PlantingSummary> thirsty = garden.growing().stream()
                .filter(p -> p.plotKind() == PlotKind.POT || p.plotKind() == PlotKind.PLANTER
                        || p.plotKind() == PlotKind.RAISED_BED)
                .toList();
        if (thirsty.isEmpty()) {
            return 0;
        }
        List<DailyForecast> days = DailyForecast.of(forecast, clock.getZone(), today, ALERT_DAYS);
        if (!DryDaysRule.dryAndWarm(days)) {
            return 0;
        }
        double warmest = days.subList(0, DryDaysRule.DAYS).stream()
                .mapToDouble(DailyForecast::maximum).max().orElseThrow();
        String names = String.join(", ", thirsty.stream()
                .map(p -> name(p) + " (" + p.plotName() + ")").distinct().toList());
        String body = String.format(Locale.ITALIAN,
                "Nei prossimi 3 giorni meno di 1 mm di pioggia e una massima di %.1f °C a %s (MET Norway, %s). "
                        + "Controlla l’acqua in: %s. Vasi, fioriere e cassoni si asciugano prima dell’orto.",
                warmest, garden.garden().name(), DryDaysRule.ID, names);
        return alerted(garden, "dry:" + garden.garden().id() + ":" + today, "Promemoria: annaffiare", body);
    }

    /** One weather alert to every member of the space; 1 when it reached someone new. */
    private int alerted(GrowingGarden garden, String key, String title, String body) {
        int notified = notifications.weatherAlert(garden.spaceId(), garden.garden().id(), key, title, body);
        return notified > 0 ? 1 : 0;
    }

    private String name(PlantingSummary p) {
        return crops.byId(p.cropId()).map(c -> c.italianName()).orElse(p.cropName());
    }
}
