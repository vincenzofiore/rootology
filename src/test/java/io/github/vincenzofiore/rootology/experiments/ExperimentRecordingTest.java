package io.github.vincenzofiore.rootology.experiments;

import io.github.vincenzofiore.rootology.inference.*;
import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

/**
 * Manually records the comparisons between Jena's answers and the LLM's
 * answers obtained in a real Claude Code session guided by the skill file.
 * This is not an automated test (there's no assertion on correctness) —
 * it's a recording mechanism: run it, read the printed report, and paste
 * the LLM answers you collected by hand into the fields below.
 */
class ExperimentRecordingTest {

    @Test
    void recordHardCase1_chainedCompanions() {
        var modelService = new JenaModelService(new OntologyDefinition());
        var runner = new ExperimentRunner(
                new CompanionQuery(modelService),
                new PestQuery(modelService),
                new ChainedRelationQuery(modelService),
                new FactPresenceQuery(modelService)
        );

        // Jena's answer is computed automatically.
        List<String> jenaAnswer = runner.jenaChainedCompanions("Tomato");

        // Paste here the answer you got from Claude Code / Copilot,
        // read directly from the conversation.
        List<String> llmAnswer = List.of("Basil");

        ExperimentResult result = runner.recordLlmAnswer(
                "hard-1",
                "Find companions of plants that repel the same pest repelled by Tomato",
                jenaAnswer,
                llmAnswer,
                "3-hop chained relation; Jena includes Tomato itself as a quirk of the closed dataset"
        );

        System.out.println(result);
    }

    @Test
    void recordHardCase2_missingFact() {
        var modelService = new JenaModelService(new OntologyDefinition());
        var runner = new ExperimentRunner(
                new CompanionQuery(modelService),
                new PestQuery(modelService),
                new ChainedRelationQuery(modelService),
                new FactPresenceQuery(modelService)
        );

        Optional<List<String>> jenaAnswer = runner.jenaDeclaredSeasonsOf("Mosquito");

        // With skill loaded: should say "not specified" -> empty list by convention.
        List<String> llmWithSkill = List.of();

        // Without skill loaded: model fell back to general statistical knowledge.
        // Paste here the actual seasons it guessed, e.g. SPRING, SUMMER.
        List<String> llmWithoutSkill = List.of("SPRING", "SUMMER", "AUTUMN");


        ExperimentResult result = runner.recordLlmAnswerWithBaseline(
                "hard-2",
                "In which season is Mosquito active?",
                jenaAnswer.orElse(List.of()),
                llmWithSkill,
                llmWithoutSkill,
                """
                        Missing-fact case. With the skill loaded, the model correctly reported the fact 
                        as not specified. Without the skill, it generalized statistically instead, 
                        producing plausible-but-unverified seasons — a concrete example of context 
                        grounding changing model behavior on the exact same question.
                        """
        );

        System.out.println(result);
    }
}
