package app.rootology.alerts;

import app.rootology.garden.GardenService;
import app.rootology.garden.GardenTime;
import app.rootology.garden.GardenViews.GardenSummary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which gardens the morning check takes, and on which of their own days (#158): a garden is due when the local
 * hour of its zone is the configured one (or always, when the hour is null), and its day is that zone's date.
 * The garden's id is grouped under the day, so the check reads what grows once per distinct day.
 */
final class GardenDays {

    /** The gardens due, by their own local day; every located garden when {@code localHour} is null. */
    static Map<LocalDate, Set<Long>> due(GardenService gardens, Clock clock, Integer localHour) {
        Map<LocalDate, Set<Long>> due = new HashMap<>();
        java.time.Instant now = clock.instant();
        for (GardenSummary garden : gardens.locatedGardens()) {
            ZoneId zone = GardenTime.zone(garden, clock);
            if (localHour != null && now.atZone(zone).getHour() != localHour) {
                continue;
            }
            due.computeIfAbsent(LocalDate.ofInstant(now, zone), day -> new HashSet<>()).add(garden.id());
        }
        return due;
    }

    private GardenDays() {
    }
}
