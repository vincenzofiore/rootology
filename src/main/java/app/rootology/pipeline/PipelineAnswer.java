package app.rootology.pipeline;

import java.util.List;

/**
 * The pipeline contestant's answer. A distinct type so it is never confused with other arms.
 * reviewer is the GitHub login of the person who answered an escalation, otherwise null.
 * sources are the ids of the knowledge base's sources behind the answer (empty for the suite);
 * notes are what the person should know, for example the forecast used or that it was missing.
 * context is the snapshot of the context the question was answered in (#55), null for a run that
 * belongs to no space (the suite's). activities are, for each value of a sowing or planting answer, the ways
 * it goes into the ground (SowingActivity names: SOW, SOW_SEEDBED, TRANSPLANT, PLANT), read from the facts
 * behind it (#112): a calendar says "in a seedbed February-March, transplant April-May", never one list.
 */
public record PipelineAnswer(
        String questionId,
        List<String> answer,
        double confidence,
        boolean escalated,
        boolean answeredByHuman,
        String reviewer,
        String text,
        List<String> sources,
        List<app.rootology.decision.Note> notes,
        ContextSnapshot context,
        java.util.Map<String, List<String>> activities) {

    public PipelineAnswer {
        activities = activities == null ? java.util.Map.of() : java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(activities));
    }

    /** An answer with no sowing or planting activities: every kind but the calendars. */
    public PipelineAnswer(String questionId, List<String> answer, double confidence, boolean escalated,
                          boolean answeredByHuman, String reviewer, String text, List<String> sources,
                          List<app.rootology.decision.Note> notes, ContextSnapshot context) {
        this(questionId, answer, confidence, escalated, answeredByHuman, reviewer, text, sources, notes, context,
                java.util.Map.of());
    }
}
