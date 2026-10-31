package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the pipeline agent on the real Embabel platform with the LLM mocked and the
 * deterministic stub decision layer forced, so the wiring is checked without any API call.
 */
@TestPropertySource(properties = "rootology.jev.api-key=")
class GardenPipelineAgentTest extends EmbabelMockitoIntegrationTest {

    @Autowired
    GardenPipeline pipeline;

    @BeforeEach
    void stubPhrasing() {
        whenGenerateText(prompt -> prompt.contains("Write a one or two sentence answer"))
                .thenReturn("phrased answer");
    }

    private PipelineAnswer ask(String id, String text, Intent intent) {
        return pipeline.ask(new Question(id, text), intent);
    }

    @Test
    void understandsTheQuestionThroughTheLlm() {
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of Tomato?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "Tomato"));

        var answer = pipeline.ask(new Question("easy-1", "Which plants are companions of Tomato?"));

        assertThat(answer.answer()).containsExactlyInAnyOrder("Basil", "Marigold");
        assertThat(answer.text()).isEqualTo("phrased answer");
        verifyCreateObject(prompt -> prompt.contains("companions of Tomato"), Intent.class);
        verifyGenerateText(prompt -> prompt.contains("Values: [") && prompt.contains("Basil"));
    }

    @Test
    void easy1CompanionsOfTomato() {
        var answer = ask("easy-1", "Which plants are companions of Tomato?",
                Intent.of(QueryKind.COMPANIONS, "Tomato"));

        assertThat(answer.answer()).containsExactlyInAnyOrder("Basil", "Marigold");
        assertThat(answer.escalated()).isFalse();
    }

    @Test
    void easy2RelevantPestsForBasilInSummer() {
        var answer = ask("easy-2", "Which pests are relevant for Basil in summer?",
                new Intent(QueryKind.RELEVANT_PESTS, "Basil", "SUMMER"));

        assertThat(answer.answer()).containsExactly("Nematode");
        assertThat(answer.escalated()).isFalse();
    }

    @Test
    void hard1CompanionsOfSharedRepellers() {
        var answer = ask("hard-1", "Find companions of plants that repel the same pest repelled by Tomato",
                Intent.of(QueryKind.COMPANIONS_OF_SHARED_REPELLERS, "Tomato"));

        // Same artifact as Jena: Basil shares Aphid with Tomato, and Basil's only companion is Tomato.
        assertThat(answer.answer()).containsExactly("Tomato");
        assertThat(answer.escalated()).isFalse();
    }

    @Test
    void hard2MosquitoSeasonIsEscalated() {
        var answer = ask("hard-2", "In which season is Mosquito active?",
                Intent.of(QueryKind.ACTIVE_SEASONS, "Mosquito"));

        assertThat(answer.answer()).isEmpty();
        assertThat(answer.escalated()).isTrue();
        verifyGenerateText(prompt -> prompt.contains("Reason: active season of Mosquito is not declared"));
    }

    @Test
    void hard3LavenderIsEscalated() {
        var answer = ask("hard-3", "Which plants are companions of Lavender?",
                Intent.of(QueryKind.COMPANIONS, "Lavender"));

        assertThat(answer.answer()).isEmpty();
        assertThat(answer.escalated()).isTrue();
        verifyGenerateText(prompt -> prompt.contains("Reason: Lavender is not in the dataset"));
    }

    @Test
    void humanReviewPausesTheAgentUntilAPersonAnswers() {
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of Lavender?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "Lavender"));
        whenGenerateText(prompt -> prompt.contains("Say that a human reviewer answered it"))
                .thenReturn("a human answered");
        var question = new Question("hard-3", "Which plants are companions of Lavender?");

        GardenPipeline.Run run = pipeline.start(question, NoiseProfile.NONE);

        assertThat(run.answer()).isNull();
        assertThat(run.pending().decision().reason()).isEqualTo("Lavender is not in the dataset");
        assertThat(pipeline.pending()).extracting(GardenPipeline.PendingReview::processId)
                .contains(run.pending().processId());

        PipelineAnswer answer = pipeline.respond(run.pending().processId(),
                new HumanAnswer(List.of(), "Lavender is outside the garden dataset"));

        assertThat(answer.answeredByHuman()).isTrue();
        assertThat(answer.escalated()).isTrue();
        assertThat(answer.answer()).isEmpty();
        assertThat(answer.text()).isEqualTo("a human answered");
        assertThat(pipeline.pending()).extracting(GardenPipeline.PendingReview::processId)
                .doesNotContain(run.pending().processId());
        verifyGenerateText(prompt -> prompt.contains("Reviewer's note: Lavender is outside the garden dataset"));
    }

    @Test
    void humanReviewModeAnswersDirectlyWhenNothingIsEscalated() {
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of Tomato?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "Tomato"));

        GardenPipeline.Run run = pipeline.start(
                new Question("easy-1", "Which plants are companions of Tomato?"), NoiseProfile.NONE);

        assertThat(run.pending()).isNull();
        assertThat(run.answer().answer()).containsExactlyInAnyOrder("Basil", "Marigold");
        assertThat(run.answer().answeredByHuman()).isFalse();
    }

    @Test
    void unsupportedQuestionIsEscalated() {
        var answer = ask("other", "What is the best fertilizer?", Intent.of(QueryKind.OTHER, null));

        assertThat(answer.escalated()).isTrue();
    }
}
