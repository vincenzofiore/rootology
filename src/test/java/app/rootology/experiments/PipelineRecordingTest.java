package app.rootology.experiments;

import app.rootology.TestcontainersConfiguration;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.Question;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

/**
 * Records the pipeline arm against Jena (the arbiter) for every experiment case.
 * Like {@link ExperimentRecordingTest}, a recording mechanism with no assertions:
 * a disagreement is an experiment outcome, not a test failure. Read the printed report.
 * Calls Claude for real, so it only runs when ROOTOLOGY_ANTHROPIC_API_KEY is set. Uses Jev when
 * ROOTOLOGY_TYPESAFE_API_KEY is set, otherwise the stub; observed results are summarized in CLAUDE.md.
 */
@Import(TestcontainersConfiguration.class)
@Tag("live-api")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ROOTOLOGY_ANTHROPIC_API_KEY", matches = ".+")
class PipelineRecordingTest {

    @Autowired
    ExperimentRunner runner;

    @Autowired
    GardenPipeline pipeline;

    private void record(String id, String text, List<String> jenaAnswer) {
        record(id, text, jenaAnswer, "");
    }

    private void record(String id, String text, List<String> jenaAnswer, String notes) {
        Question question = new Question(id, text);
        System.out.println(PipelineResult.of(question, jenaAnswer, pipeline.ask(question), notes));
    }

    @Test
    void recordAllCases() {
        record("easy-1", "Which plants are companions of Tomato?",
                runner.jenaCompanionsOf("Tomato"));

        record("easy-2", "Which pests are relevant for Basil in summer?",
                runner.jenaRelevantPests("Basil", "SUMMER"));

        record("hard-1", "Find companions of plants that repel the same pest repelled by Tomato",
                runner.jenaChainedCompanions("Tomato"),
                """
                        With StubDecisionLayer, first real run (2026-10-31, claude-sonnet-5-5): intent and decision were correct
                        ([Tomato], same artifact as Jena), but the phrasing step contradicted them, saying it
                        could not identify the companions. The recorded values stay correct because they come
                        from the decision layer, not from the text. Kept as is, prompt deliberately not tuned:
                        a phrasing-only LLM can still undermine a correct decision.
                        """);

        record("hard-2", "In which season is Mosquito active?",
                runner.jenaDeclaredSeasonsOf("Mosquito").orElse(List.of()));

        record("hard-3", "Which plants are companions of Lavender?",
                runner.jenaCompanionsOf("Lavender"));
    }
}
