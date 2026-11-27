package app.rootology.garden;

import app.rootology.garden.GardenViews.GardenSummary;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** The garden's own day (#158): its stored zone, else the Clock bean's, never the machine's. */
class GardenTimeTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");
    /** 03:00 in Rome on 1 December: still 30 November in New York. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-01T02:00:00Z"), ROME);

    @Test
    void aGardenInAnotherZoneHasItsOwnDateAndMonthNearMidnight() {
        GardenSummary newYork = garden("America/New_York");

        assertThat(GardenTime.today(newYork, CLOCK)).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(GardenTime.today(newYork, CLOCK).getMonthValue()).as("still November there").isEqualTo(11);
        assertThat(GardenTime.today(garden("Europe/Rome"), CLOCK)).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(GardenTime.zone(newYork, CLOCK)).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    void withoutAStoredZoneTheAppOwnIsUsed() {
        assertThat(GardenTime.zone(garden(null), CLOCK)).isEqualTo(ROME);
        assertThat(GardenTime.zone((GardenSummary) null, CLOCK)).as("a run with no garden").isEqualTo(ROME);
        assertThat(GardenTime.today(garden(null), CLOCK)).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(GardenTime.zone((ZoneId) null, CLOCK)).isEqualTo(ROME);
        assertThat(GardenTime.today((ZoneId) null, CLOCK)).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void anIdThisJvmDoesNotKnowIsNoZoneAndIsNeverStored() {
        assertThat(GardenTime.zone(garden("Nowhere/Land"), CLOCK)).as("never guessed, no exception").isEqualTo(ROME);
        assertThat(GardenTime.known("Europe/Paris")).isTrue();
        assertThat(GardenTime.known("Nowhere/Land")).isFalse();
        assertThat(GardenTime.known(null)).isFalse();
        assertThat(GardenTime.known(" ")).isFalse();
    }

    private static GardenSummary garden(String timeZone) {
        return new GardenSummary(1, "Orto", null, null, null, null, null, null, timeZone);
    }
}
