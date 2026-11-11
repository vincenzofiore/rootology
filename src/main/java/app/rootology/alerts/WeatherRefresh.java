package app.rootology.alerts;

import app.rootology.garden.GardenLocated;
import app.rootology.garden.GardenService;
import app.rootology.weather.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps a stored forecast for every garden with coordinates, so the gardens map's popups always have the
 * weather now (#67), which reads only what is stored. A garden that gets a new place is fetched at once, in
 * the background after its save commits; every garden is refreshed every few hours, also those with nothing
 * growing, which the morning alerts skip. {@link WeatherService#around} keeps MET Norway's rules: a copy
 * that has not expired is not fetched again, and a failing service costs nothing but a log line.
 * Off in tests ({@code rootology.weather.refresh.enabled=false}), so no test calls MET Norway.
 */
@Component
@ConditionalOnProperty(name = "rootology.weather.refresh.enabled", havingValue = "true", matchIfMissing = true)
class WeatherRefresh {

    private static final Logger log = LoggerFactory.getLogger(WeatherRefresh.class);

    private final GardenService gardens;
    private final WeatherService weather;
    private final Clock clock;

    WeatherRefresh(GardenService gardens, WeatherService weather, Clock clock) {
        this.gardens = gardens;
        this.weather = weather;
        this.clock = clock;
    }

    /** A new place: its forecast now, so the popup shows it at the next look. The save never waits. */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    void on(GardenLocated located) {
        weather.around(located.latitude(), located.longitude());
    }

    @Scheduled(cron = "${rootology.weather.refresh.cron}", zone = "Europe/Rome")
    void everyFewHours() {
        log.info("Weather refreshed for {} places", refresh());
    }

    /** Every place with a garden, once even when several gardens share it; returns how many places. */
    int refresh() {
        Set<List<BigDecimal>> places = new LinkedHashSet<>();
        gardens.locatedGardensGrowing(LocalDate.now(clock)).forEach(g -> places.add(List.of(
                g.garden().latitude().setScale(2, RoundingMode.HALF_UP),
                g.garden().longitude().setScale(2, RoundingMode.HALF_UP))));
        places.forEach(place -> weather.around(place.get(0), place.get(1)));
        return places.size();
    }
}
