package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.Note;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlotSummary;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything one question is answered with, resolved once per run (#55): the run's space and garden, the
 * effective intent (zone and month already chosen: the question's, then the garden's, then the defaults),
 * the plot a question names, the place of the forecast, the notes that describe the context, and the
 * refusal when the context cannot answer at all. Internal to the pipeline in the sense that nothing
 * outside it reads the record: what is shown and stored is {@link #snapshot()}.
 *
 * <p>refusal is the reason of a deterministic escalation decided before the decision layer (a garden
 * question asked outside a garden, a garden that no longer exists, a plot not found or ambiguous); it is
 * null when the question can go to the decision layer. The snapshot is null when there is nothing true to
 * show: a run that belongs to no space, and a garden that no longer exists.
 */
public record QuestionContext(
        long spaceId,
        long gardenId,
        Intent intent,
        Plot plot,
        Location weather,
        ContextSnapshot snapshot,
        String refusal,
        List<Note> notes) {

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

    /** Where the forecast is for: the run's garden's coordinates, when it has them. */
    public record Location(BigDecimal latitude, BigDecimal longitude) {
    }

    public QuestionContext {
        notes = List.copyOf(notes);
    }

    /** For a run that belongs to no space: no garden, no notes, no context shown (the suite's runs). */
    static QuestionContext none(Intent intent) {
        return new QuestionContext(0, 0, intent, null, null, null, null, List.of());
    }

    /** The context of a question whose agent was lost: only what was stored, never recomputed. */
    static QuestionContext stored(ContextSnapshot snapshot) {
        return new QuestionContext(0, 0, null, null, null, snapshot, null, List.of());
    }

    boolean hasSpace() {
        return spaceId > 0;
    }
}
