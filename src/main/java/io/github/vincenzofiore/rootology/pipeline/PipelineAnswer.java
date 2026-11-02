package io.github.vincenzofiore.rootology.pipeline;

import java.util.List;

/**
 * The pipeline contestant's answer. A distinct type so it is never confused with other arms.
 * reviewer is the GitHub login of the person who answered an escalation, otherwise null.
 */
public record PipelineAnswer(
        String questionId,
        List<String> answer,
        double confidence,
        boolean escalated,
        boolean answeredByHuman,
        String reviewer,
        String text) {
}
