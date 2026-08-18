package io.github.vincenzofiore.rootology.experiments;

import java.util.HashSet;
import java.util.List;

/**
 * Captures a single comparison between the Jena-derived answer and the
 * LLM-derived answer (obtained manually, by asking Claude Code / Copilot
 * with the rootology-garden-companion skill loaded) for the same question.
 */
public record ExperimentResult(
        String questionId,
        String question,
        List<String> jenaAnswer,
        List<String> llmWithSkillAnswer,
        List<String> llmWithoutSkillAnswer,
        boolean matches,
        String notes
) {

    /** Convenience factory used when only the "with skill" arm was collected. */
    public static ExperimentResult of(String questionId, String question,
                                      List<String> jenaAnswer, List<String> llmWithSkillAnswer,
                                      String notes) {
        return withBaseline(questionId, question, jenaAnswer, llmWithSkillAnswer, List.of(), notes);
    }

    /** Full factory including the "without skill" baseline for contrast. */
    public static ExperimentResult withBaseline(String questionId, String question,
                                                List<String> jenaAnswer,
                                                List<String> llmWithSkillAnswer,
                                                List<String> llmWithoutSkillAnswer,
                                                String notes) {
        boolean skillMatchesJena = jenaAnswer.size() == llmWithSkillAnswer.size()
                && new HashSet<>(jenaAnswer).containsAll(llmWithSkillAnswer);
        return new ExperimentResult(questionId, question, jenaAnswer,
                llmWithSkillAnswer, llmWithoutSkillAnswer, skillMatchesJena, notes);
    }
}
