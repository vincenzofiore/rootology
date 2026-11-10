package io.github.vincenzofiore.rootology.facts;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The climate profile of a site (#62): the nearest AgERA5 cell, the garden's altitude when it is known,
 * the frost dates corrected for altitude, and the two safe months the window rule uses. A profile with a
 * zone is a reference site named in the question (NORD, CENTRO or SUD); a profile without one is a garden's.
 *
 * <p>lastFrostDay and firstFrostDay are days of the year, null when the site has no such frost. The
 * correction says what the altitude did to the cell's dates, so the person can be told.
 */
public record SiteProfile(
        String zone,
        double latitude,
        double longitude,
        int cellElevationM,
        Integer altitudeM,
        double landFraction,
        Integer lastFrostDay,
        Integer firstFrostDay,
        int firstSafeMonth,
        int lastSafeMonth,
        Correction correction,
        List<String> sources) {

    /** What the altitude correction did; the pipeline words it as a note. */
    public enum Correction {
        /** No correction was needed (the altitude is the cell's). */
        NONE,
        /** The dates were moved by the altitude difference. */
        APPLIED,
        /** The altitude is not known: the cell's dates were used as they are. */
        MISSING,
        /** The garden is on the coast, below the cell: the cell's dates were used as they are. */
        COAST,
        /** The altitude difference is more than 500 m: the correction was capped at 500 m. */
        CAPPED
    }

    public SiteProfile {
        sources = List.copyOf(sources);
    }

    /** True for a garden's own profile, false for a reference site named in the question. */
    public boolean fromGarden() {
        return zone == null;
    }

    /** The cell as it is shown, for example "45.1/7.7". */
    public String cell() {
        return String.format(Locale.ROOT, "%.1f/%.1f", latitude, longitude);
    }

    /** The profile in English, for the decision layer's prompt and for logs; never shown to a person. */
    public String english() {
        return "Climate profile of the site: last frost (1 year in 5) "
                + (lastFrostDay == null ? "none in spring" : "on " + day(lastFrostDay))
                + ", first frost (1 year in 5) "
                + (firstFrostDay == null ? "none in autumn" : "on " + day(firstFrostDay))
                + "; altitude " + (altitudeM == null ? "not known" : altitudeM + " m")
                + "; cell " + cell() + "; AgERA5 1996-2025.";
    }

    private static String day(int dayOfYear) {
        return DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
                .format(LocalDate.of(2025, 1, 1).plusDays(dayOfYear - 1L));
    }
}
