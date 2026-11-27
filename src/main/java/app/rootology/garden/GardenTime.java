package app.rootology.garden;

import app.rootology.garden.GardenViews.GardenSummary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * The garden's own day (#158): its local time comes from its coordinates, stored as an IANA zone when the
 * coordinates are saved (#62), not from the person asking. Without a stored zone the app's own is used, the
 * Clock bean's (Europe/Rome), which is what the whole app had before.
 *
 * <p>An id this JVM does not know is no zone at all, and the app's own is used instead.
 */
public final class GardenTime {

    /** The garden's zone, else the app's own (the Clock bean's): never null. */
    public static ZoneId zone(GardenSummary garden, Clock clock) {
        return garden == null ? clock.getZone() : zone(garden.timeZone(), clock);
    }

    /**
     * The zone of a garden read from a row by its stored id (#158): the app's own (the Clock bean's) when there
     * is none or this JVM does not know it. Never null.
     */
    public static ZoneId zone(String storedId, Clock clock) {
        return storedId == null ? clock.getZone() : stored(storedId).orElse(clock.getZone());
    }

    /**
     * The zone of a run whose garden is known by its id only: the zone the context resolved, or the Clock
     * bean's when the run has none (no garden, or a context rebuilt from the history).
     */
    public static ZoneId zone(ZoneId resolved, Clock clock) {
        return resolved == null ? clock.getZone() : resolved;
    }

    /** The date at the garden now. */
    public static LocalDate today(GardenSummary garden, Clock clock) {
        return LocalDate.ofInstant(clock.instant(), zone(garden, clock));
    }

    /** The date now in the zone a run resolved, or in the Clock bean's when it resolved none. */
    public static LocalDate today(ZoneId resolved, Clock clock) {
        return LocalDate.ofInstant(clock.instant(), zone(resolved, clock));
    }

    /** Whether the id is an IANA zone name this JVM knows: what may be stored as a garden's zone. */
    public static boolean known(String id) {
        return id != null && stored(id).isPresent();
    }

    private static Optional<ZoneId> stored(String id) {
        if (id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZoneId.of(id));
        } catch (java.time.DateTimeException unknown) {
            return Optional.empty();
        }
    }

    private GardenTime() {
    }
}
