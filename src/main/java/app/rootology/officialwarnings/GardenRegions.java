package app.rootology.officialwarnings;

import app.rootology.garden.GardenLocated;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.geocoding.GeoNames;
import app.rootology.geocoding.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Objects;

/**
 * Fills a garden's region from GeoNames (#136), so its official MeteoAlarm warnings can be found. A garden
 * that gets a place is looked up in the background after its save commits; gardens still without a region
 * are looked up once at startup and every night, at most {@value #MAX_PER_RUN} per run and one call at a time.
 * GeoNames disabled or down: nothing happens and the next run tries again. Off in tests
 * ({@code rootology.meteoalarm.enabled=false}).
 */
@Component
@ConditionalOnProperty(name = "rootology.meteoalarm.enabled", havingValue = "true", matchIfMissing = true)
class GardenRegions {

    static final int MAX_PER_RUN = 50;
    private static final String LANGUAGE = "it";

    private static final Logger log = LoggerFactory.getLogger(GardenRegions.class);

    private final GardenService gardens;
    private final GeoNames geoNames;

    GardenRegions(GardenService gardens, GeoNames geoNames) {
        this.gardens = gardens;
        this.geoNames = geoNames;
    }

    /** A new place: its region now, so the warnings are there at the next look. The save never waits. */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    void on(GardenLocated located) {
        fill(located.gardenId(), located.latitude().doubleValue(), located.longitude().doubleValue());
    }

    /** Gardens saved before the region was recorded, looked up once the app is up. */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    void atStartup() {
        fillMissing();
    }

    /** The same, every night, for gardens whose region could not be found the first time. */
    @Scheduled(cron = "0 15 3 * * *", zone = "Europe/Rome")
    void everyNight() {
        log.info("Regions filled for {} gardens", fillMissing());
    }

    /** At most {@value #MAX_PER_RUN} gardens, one GeoNames call at a time. Returns how many were tried. */
    int fillMissing() {
        List<GardenSummary> waiting = gardens.withoutRegion();
        int tried = 0;
        for (GardenSummary garden : waiting) {
            if (tried >= MAX_PER_RUN) {
                break;
            }
            fill(garden.id(), garden.latitude().doubleValue(), garden.longitude().doubleValue());
            tried++;
        }
        return tried;
    }

    /** One lookup; nothing when GeoNames is disabled, down or has no place there, or the place is not Italian. */
    private void fill(long gardenId, double latitude, double longitude) {
        geoNames.nearestPlace(latitude, longitude, LANGUAGE)
                .filter(place -> "IT".equals(place.countryCode()))
                .map(Place::adminCode1)
                .filter(Objects::nonNull)
                .ifPresent(region -> gardens.setRegion(gardenId, region));
    }
}
