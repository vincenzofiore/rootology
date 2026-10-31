package io.github.vincenzofiore.rootology.experiments;

import io.github.vincenzofiore.rootology.pipeline.PipelineAnswer;
import io.github.vincenzofiore.rootology.pipeline.Question;

import java.util.HashSet;
import java.util.List;

/**
 * Sibling of {@link ExperimentResult} for the pipeline arm (Embabel + Claude + decision layer).
 * Kept separate so the hand-recorded LLM arms stay untouched. Unlike those, this arm is
 * computed automatically.
 */
public record PipelineResult(
        String questionId,
        String question,
        List<String> jenaAnswer,
        List<String> pipelineAnswer,
        double confidence,
        boolean escalated,
        boolean matches,
        String pipelineText,
        String notes
) {

    public static PipelineResult of(Question question, List<String> jenaAnswer, PipelineAnswer answer,
                                    String notes) {
        boolean matches = new HashSet<>(jenaAnswer).equals(new HashSet<>(answer.answer()));
        return new PipelineResult(question.id(), question.text(), jenaAnswer, answer.answer(),
                answer.confidence(), answer.escalated(), matches, answer.text(), notes);
    }
}
