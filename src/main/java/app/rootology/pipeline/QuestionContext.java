package app.rootology.pipeline;

import app.rootology.decision.Intent;
import app.rootology.decision.Note;
import app.rootology.facts.SiteProfile;
import app.rootology.garden.GardenViews.PlotSummary;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything one question is answered with, resolved once per run (#55): the run's space and garden, the
 * effective intent (zone, place and month already chosen: the question's, then the garden's, then the
 * defaults), the plot a question names, the place of the forecast, the climate profile of the site the
 * calendars read, the notes that describe the context, the refusal when the context cannot answer at all and
 * the choice the person has to make when it can answer only after they pick a place or a garden (#107).
 * Internal to the pipeline in the sense that nothing
 * outside it reads the record: what is shown and stored is {@link #snapshot()}.
 *
 * <p>refusal is the reason of a deterministic escalation decided before the decision layer (a garden
 * question asked outside a garden, a garden that no longer exists, a plot not found or ambiguous); it is
 * null when the question can go to the decision layer. The snapshot is null when there is nothing true to
 * show: a run that belongs to no space, a garden that no longer exists, and a run that only asks a choice.
 * domainUnjudged says the domain filter could not judge the question and let it through (#161, #165), so the
 * run's history can say it went on unscreened; it is false for a run the filter never saw.
 */
public record QuestionContext(
        long spaceId,
        long gardenId,
        Intent intent,
        Plot plot,
        Location weather,
        SiteProfile climate,
        ContextSnapshot snapshot,
        String refusal,
        List<Note> notes,
        Choice choice,
        Choice.Place place,
        boolean domainUnjudged) {

    /** What a SOW_IN_PLOT question asked for, and what the run's garden answered. */
    public record Plot(String askedName, Outcome outcome, PlotSummary found, List<String> matches) {

        public enum Outcome {
            /** Found in the run's garden: the question can be answered. */
            FOUND,
            /** Not found in the run's garden: the question waits for a person. */
            NOT_FOUND,
            /** Matching several of its plots: the question waits for a person. */
            AMBIGUOUS
        }
    }

    /**
     * Where the forecast is for: the run's garden's coordinates and altitude, or the place's, when they have
     * them. The altitude is null when not known, and MET Norway then uses its own topography (#135).
     */
    public record Location(BigDecimal latitude, BigDecimal longitude, Integer elevationM) {
    }

    /**
     * A question that can be answered only once the person picks a place or a garden (#107): a calendar
     * question outside a garden with no place and no zone, or a place not found or matching several; a
     * garden question outside a garden, or a garden name matching several. The options are what the person
     * chooses from; the UI adds a way to name another place.
     */
    public record Choice(Kind kind, Why why, List<Place> places, List<Garden> gardens) {

        public enum Kind {
            PLACE, GARDEN
        }

        /** Missing: nothing was named. Not found, ambiguous: what the question named. */
        public enum Why {
            MISSING, NOT_FOUND, AMBIGUOUS
        }

        /** A place the person may choose: its names, coordinates and altitude, when known. */
        public record Place(String name, String region, String countryCode, String countryName,
                            BigDecimal latitude, BigDecimal longitude, Integer elevationM) {

            /** How the place is shown in the list, for example "Sant'Angelo, Lazio" (#87). */
            public String label() {
                return name + (region == null || region.isBlank() || region.equals(name) ? "" : ", " + region)
                        + (countryName == null || countryName.isBlank() || "IT".equals(countryCode) ? ""
                        : ", " + countryName);
            }
        }

        /** A garden of the space the person may choose. */
        public record Garden(long id, String name) {
        }

        public Choice {
            places = List.copyOf(places);
            gardens = List.copyOf(gardens);
        }

        static Choice place(Why why, List<Place> places) {
            return new Choice(Kind.PLACE, why, places, List.of());
        }

        static Choice garden(Why why, List<Garden> gardens) {
            return new Choice(Kind.GARDEN, why, List.of(), gardens);
        }
    }

    /**
     * The place the person chose for a continued question (#107), on the blackboard so the context resolver
     * reads it instead of looking the place up again, and whether the domain filter could not judge the
     * question (#165, {@link #DOMAIN_UNJUDGED}); {@link #NONE} for every other run.
     */
    public record Pinned(Choice.Place place, boolean domainUnjudged) {

        public static final Pinned NONE = new Pinned(null, false);

        /** The question went on unscreened: the filter was asked but could not judge it (#161). */
        public static final Pinned DOMAIN_UNJUDGED = new Pinned(null, true);

        boolean isNone() {
            return place == null;
        }
    }

    public QuestionContext {
        notes = List.copyOf(notes);
    }

    /** For a run that belongs to no space: no garden, no notes, no context shown (the suite's runs). */
    static QuestionContext none(Intent intent) {
        return new QuestionContext(0, 0, intent, null, null, null, null, null, List.of(), null, null, false);
    }

    /** The context of a question whose agent was lost: only what was stored, never recomputed. */
    static QuestionContext stored(ContextSnapshot snapshot) {
        return new QuestionContext(0, 0, null, null, null, null, snapshot, null, List.of(), null, null, false);
    }

    boolean hasSpace() {
        return spaceId > 0;
    }

    /** The same context, saying the domain filter could not judge the question (#165). */
    QuestionContext markDomainUnjudged() {
        return new QuestionContext(spaceId, gardenId, intent, plot, weather, climate, snapshot, refusal, notes,
                choice, place, true);
    }
}
