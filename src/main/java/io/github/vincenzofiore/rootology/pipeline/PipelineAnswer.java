package io.github.vincenzofiore.rootology.pipeline;

import java.util.List;

/**
 * The pipeline contestant's answer. A distinct type so it is never confused with other arms.
 * reviewer is the GitHub login of the person who answered an escalation, otherwise null.
 * sources are the ids of the knowledge base's sources behind the answer (empty for the suite);
 * notes are what the person should know, for example the forecast used or that it was missing.
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
        List<io.github.vincenzofiore.rootology.decision.Note> notes) {
}
