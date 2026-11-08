package io.github.vincenzofiore.rootology.pipeline;

/**
 * The context a run used, as it is shown and stored (#55): the names as they were, the zone and the month
 * with where they came from, and the plot if the question named one. A snapshot, not the internal record,
 * so a stored row stays readable when the pipeline's types change; gardenName is null at the space level,
 * and the whole snapshot is null for a run that belongs to no space (the suite's).
 */
public record ContextSnapshot(
        String spaceName,
        String gardenName,
        String zone,
        ZoneFrom zoneFrom,
        String month,
        MonthFrom monthFrom,
        String plotName) {

    /** Where the zone came from: the question, the run's garden, or the default (CENTRO). */
    public enum ZoneFrom {
        QUESTION, GARDEN, DEFAULT
    }

    /** Where the month came from: the question, or the current month in Italy. */
    public enum MonthFrom {
        QUESTION, DEFAULT
    }

    public boolean hasGarden() {
        return gardenName != null;
    }
}
