package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.history.PipelineRunRecord;
import io.github.vincenzofiore.rootology.history.RunHistory;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the pipeline agent on the real Embabel platform with the LLM mocked and the
 * deterministic stub decision layer forced, so the wiring is checked without any API call.
 */
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "rootology.jev.api-key=")
class GardenPipelineAgentTest extends EmbabelMockitoIntegrationTest {

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    RunHistory history;

    @Autowired
    SpaceService spaces;

    /** A person with their personal space, as after their first login. */
    private RunContext signedIn(String login) {
        spaces.signedIn(login);
        return new RunContext(spaces.spacesOf(login).getFirst().id(), login);
    }

    private PipelineRunRecord stored(long spaceId, String processId) {
        return history.recent(spaceId).stream().filter(r -> r.getProcessId().equals(processId)).findFirst().orElseThrow();
    }

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
        RunContext alice = signedIn("alice");

        GardenPipeline.Run run = pipeline.start(question, NoiseProfile.NONE, alice);

        assertThat(run.answer()).isNull();
        assertThat(run.pending().decision().reason()).isEqualTo("Lavender is not in the dataset");
        assertThat(pipeline.pending(alice.spaceId())).extracting(GardenPipeline.PendingReview::processId)
                .contains(run.pending().processId());

        PipelineAnswer answer = pipeline.respond(run.pending().processId(), alice.spaceId(),
                new HumanAnswer(List.of(), "Lavender is outside the garden dataset", "a-reviewer"));

        assertThat(answer.answeredByHuman()).isTrue();
        assertThat(answer.reviewer()).isEqualTo("a-reviewer");
        assertThat(answer.escalated()).isTrue();
        assertThat(answer.answer()).isEmpty();
        assertThat(answer.text()).isEqualTo("a human answered");
        assertThat(pipeline.pending(alice.spaceId())).extracting(GardenPipeline.PendingReview::processId)
                .doesNotContain(run.pending().processId());
        verifyGenerateText(prompt -> prompt.contains("Reviewer's note: Lavender is outside the garden dataset"));

        PipelineRunRecord record = stored(alice.spaceId(), run.pending().processId());
        assertThat(record.getQuestionId()).isEqualTo("hard-3");
        assertThat(record.getStartedBy()).isEqualTo("alice");
        assertThat(record.isDecisionEscalated()).isTrue();
        assertThat(record.isAnsweredByHuman()).isTrue();
        assertThat(record.getReviewer()).isEqualTo("a-reviewer");
        assertThat(record.getJenaAnswer()).isEmpty();
        assertThat(record.getMatchesJena()).isTrue();
    }

    @Test
    void humanReviewModeAnswersDirectlyWhenNothingIsEscalated() {
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of Tomato?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "Tomato"));

        RunContext bob = signedIn("bob");
        GardenPipeline.Run run = pipeline.start(
                new Question("easy-1", "Which plants are companions of Tomato?"), NoiseProfile.NONE, bob);

        assertThat(run.pending()).isNull();
        assertThat(run.answer().answer()).containsExactlyInAnyOrder("Basil", "Marigold");
        assertThat(run.answer().answeredByHuman()).isFalse();
        assertThat(run.answer().reviewer()).isNull();

        PipelineRunRecord record = history.recent(bob.spaceId()).getFirst();
        assertThat(record.getQuestionId()).isEqualTo("easy-1");
        assertThat(record.getAnswer()).containsExactlyInAnyOrder("Basil", "Marigold");
        assertThat(record.getJenaAnswer()).containsExactlyInAnyOrder("Basil", "Marigold");
        assertThat(record.getMatchesJena()).isTrue();
        assertThat(record.isNoisy()).isFalse();
    }

    @Test
    void spacesNeverSeeEachOthersEscalationsOrHistory() {
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of Lavender?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "Lavender"));
        whenGenerateText(prompt -> prompt.contains("Say that a human reviewer answered it")).thenReturn("answered");
        RunContext carol = signedIn("carol");
        RunContext dave = signedIn("dave");

        GardenPipeline.Run run = pipeline.start(
                new Question("hard-3", "Which plants are companions of Lavender?"), NoiseProfile.NONE, carol);
        String processId = run.pending().processId();

        assertThat(pipeline.pending(dave.spaceId())).extracting(GardenPipeline.PendingReview::processId)
                .doesNotContain(processId);
        assertThatThrownBy(() -> pipeline.respond(processId, dave.spaceId(), new HumanAnswer(List.of(), "", "dave")))
                .isInstanceOf(NoSuchElementException.class);

        pipeline.respond(processId, carol.spaceId(), new HumanAnswer(List.of(), "", "carol"));
        assertThat(history.recent(carol.spaceId())).extracting(PipelineRunRecord::getProcessId).contains(processId);
        assertThat(history.recent(dave.spaceId())).extracting(PipelineRunRecord::getProcessId).doesNotContain(processId);
    }

    @Test
    void realQuestionsAreAnsweredFromTheKnowledgeBaseWithSources() {
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of tomato?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "tomato"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(
                new Question("ask", "Which plants are companions of tomato?"), signedIn("maria"));

        assertThat(run.answer().answer()).containsExactlyInAnyOrder("Basil", "Onion", "Parsley", "Carrot");
        assertThat(run.answer().sources()).contains("agronet-basil-2016", "cornell-1999");
    }

    @Test
    void aQuestionInItalianGetsValuesAndAnAnswerInItalian() {
        whenCreateObject(prompt -> prompt.contains("Question: Quali piante sono compagne del pomodoro?"), Intent.class)
                .thenReturn(new Intent(QueryKind.COMPANIONS, "Tomato", null, "it"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(
                new Question("ask", "Quali piante sono compagne del pomodoro?"), signedIn("paola"));

        assertThat(run.answer().answer()).containsExactlyInAnyOrder("Basilico", "Cipolla", "Prezzemolo", "Carota");
        verifyGenerateText(prompt -> prompt.contains("in the language of the question")
                && prompt.contains("Values: [") && prompt.contains("Basilico"));
    }

    @Test
    void relevantPestsComeFromWhatAffectsTheCropInTheSeason() {
        whenCreateObject(prompt -> prompt.contains("Question: Which pests are relevant for tomato in summer?"), Intent.class)
                .thenReturn(new Intent(QueryKind.RELEVANT_PESTS, "tomato", "SUMMER"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(
                new Question("ask", "Which pests are relevant for tomato in summer?"), signedIn("nora"));

        assertThat(run.answer().answer()).contains("Late blight", "Colorado potato beetle", "Tomato leafminer")
                .doesNotContain("Carrot fly");
        assertThat(run.answer().sources()).contains("eppo-codes", "rootology-editorial");
    }

    @Test
    void anUnknownCropInARealQuestionWaitsForAPerson() {
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of lavender?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "lavender"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(
                new Question("ask", "Which plants are companions of lavender?"), signedIn("omar"));

        assertThat(run.answer()).isNull();
        assertThat(run.pending().decision().reason()).isEqualTo("lavender is not in the dataset");
    }

    @Test
    void unsupportedQuestionIsEscalated() {
        var answer = ask("other", "What is the best fertilizer?", Intent.of(QueryKind.OTHER, null));

        assertThat(answer.escalated()).isTrue();
    }
}
