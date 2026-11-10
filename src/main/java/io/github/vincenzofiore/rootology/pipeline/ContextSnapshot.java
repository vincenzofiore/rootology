package io.github.vincenzofiore.rootology.pipeline;

/**
 * The context a run used, as it is shown and stored (#55, #62): the names as they were, the zone when the
 * question named one, the month with where it came from, the plot if the question named one, and the
 * climate profile the calendars read (the last and first frost as days of the year, null when there is
 * none, and where the profile came from). A snapshot, not the internal record, so a stored row stays
 * readable when the pipeline's types change; gardenName is null at the space level, and the whole snapshot
 * is null for a run that belongs to no space (the suite's). Rows stored before #62 have no climate fields.
 */
public record ContextSnapshot(
        String spaceName,
        String gardenName,
        String zone,
        ZoneFrom zoneFrom,
        String month,
        MonthFrom monthFrom,
        String plotName,
        Integer lastFrostDay,
        Integer firstFrostDay,
        ClimateFrom climateFrom) {

    /** Where the zone came from: the question, the run's garden, or the default (CENTRO); null when none. */
    public enum ZoneFrom {
        QUESTION, GARDEN, DEFAULT
    }

    /** Where the month came from: the question, or the current month in Italy. */
    public enum MonthFrom {
        QUESTION, DEFAULT
    }

    /** Where the climate profile came from: the run's garden, a zone named in the question, or the default. */
    public enum ClimateFrom {
        GARDEN, QUESTION, DEFAULT
    }

    /** A snapshot without climate fields, for rows stored before #62 and for runs with no calendar. */
    public ContextSnapshot(String spaceName, String gardenName, String zone, ZoneFrom zoneFrom, String month,
                           MonthFrom monthFrom, String plotName) {
        this(spaceName, gardenName, zone, zoneFrom, month, monthFrom, plotName, null, null, null);
    }

    public boolean hasGarden() {
        return gardenName != null;
    }

    /** True when the context carried a climate profile (the calendar questions do). */
    public boolean hasClimate() {
        return climateFrom != null;
    }
}
