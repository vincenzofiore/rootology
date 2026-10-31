package io.github.vincenzofiore.rootology.pipeline;

import java.util.List;

/** The pipeline contestant's answer. A distinct type so it is never confused with other arms. */
public record PipelineAnswer(
        String questionId,
        List<String> answer,
        double confidence,
        boolean escalated,
        String text) {
}
