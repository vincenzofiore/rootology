package io.github.vincenzofiore.rootology.pipeline;

import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.facts.Contribution;
import io.github.vincenzofiore.rootology.facts.KnowledgeContributions;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.PlantingMethod;
import io.github.vincenzofiore.rootology.garden.PlotKind;
import io.github.vincenzofiore.rootology.garden.Soil;
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

    @Autowired
    GardenService gardens;

    @Autowired
    KnowledgeContributions contributions;

    @Autowired
    ClaudeCircuit claude;

    @org.junit.jupiter.api.AfterEach
    void closeClaudesCircuit() {
        claude.breaker.success();
    }

    @Test
    void whenClaudeCannotUnderstandTheQuestionThePersonIsToldClearly() {
        whenCreateObject(prompt -> prompt.contains("Question: Is Claude down?"), Intent.class)
                .thenThrow(new IllegalStateException("overloaded"));

        assertThatThrownBy(() -> pipeline.askKnowledgeBase(new Question("ask", "Is Claude down?"), signedIn("dora")))
                .isInstanceOf(io.github.vincenzofiore.rootology.resilience.ServiceUnavailableException.class)
                .hasMessageContaining("Claude is not available");
    }

    @Test
    void anOpenCircuitRefusesAtOnce() {
        for (int i = 0; i < io.github.vincenzofiore.rootology.resilience.CircuitBreaker.FAILURE_THRESHOLD; i++) {
            claude.breaker.failure();
        }

        assertThatThrownBy(() -> pipeline.askKnowledgeBase(new Question("ask", "Anything?"), signedIn("ezra")))
                .isInstanceOf(io.github.vincenzofiore.rootology.resilience.ServiceUnavailableException.class);
        verifyNoInteractions();
    }

    @Test
    void whenClaudeCannotPhraseTheAnswerThePlainValuesAreShown() {
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of tomato?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "tomato"));
        whenGenerateText(prompt -> prompt.contains("Question: Which plants are companions of tomato?"))
                .thenThrow(new IllegalStateException("overloaded"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(
                new Question("ask", "Which plants are companions of tomato?"), signedIn("fede"));

        assertThat(run.answer().text()).startsWith("Answer: ").contains("Basil");
    }

    /** Never the real service in tests: by default it has no forecast, as when MET Norway is down. */
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    io.github.vincenzofiore.rootology.weather.WeatherService weatherService;

    /** A space with a garden in Milan (zone NORD): bed B with a tomato since April 2026, an old pea ended. */
    private RunContext withGarden(String login) {
        RunContext run = signedIn(login);
        var garden = gardens.saveGarden(run.spaceId(), login, null, "Balcony", "Milano",
                new java.math.BigDecimal("45.46"), new java.math.BigDecimal("9.19"));
        var bed = gardens.savePlot(run.spaceId(), login, garden.id(), null, "Bed B", PlotKind.GROUND, Soil.LOAMY, null);
        gardens.plant(run.spaceId(), login, bed.id(), "tomato", PlantingMethod.TRANSPLANTED,
                java.time.LocalDate.of(2026, 4, 20), null);
        var peas = gardens.plant(run.spaceId(), login, bed.id(), "pea", PlantingMethod.SOWN,
                java.time.LocalDate.of(2026, 2, 1), null);
        gardens.endPlanting(run.spaceId(), login, peas.id(), java.time.LocalDate.of(2026, 4, 1));
        return run;
    }

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
    void aSpaceSeesItsOwnProposedFactsAndNamesOthersDoNot() {
        RunContext rita = signedIn("rita");
        RunContext sam = signedIn("sam");
        // As if a member of rita's space had added them (#24): proposal ids only label the sources here.
        contributions.apply(9001, rita.spaceId(), new Contribution.Alias("tomato", "pomodorino", "it"));
        contributions.apply(9002, rita.spaceId(), new Contribution.Pair("tomato", "lettuce", "COMPANION", "Shade"));
        whenCreateObject(prompt -> prompt.contains("Question: Quali piante sono compagne del pomodorino?"), Intent.class)
                .thenReturn(new Intent(QueryKind.COMPANIONS, "pomodorino", null, "it"));

        GardenPipeline.Run mine = pipeline.askKnowledgeBase(
                new Question("ask", "Quali piante sono compagne del pomodorino?"), rita);
        GardenPipeline.Run theirs = pipeline.askKnowledgeBase(
                new Question("ask", "Quali piante sono compagne del pomodorino?"), sam);

        assertThat(mine.answer().answer()).containsExactlyInAnyOrder("Basilico", "Cipolla", "Prezzemolo", "Carota",
                "Lattuga");
        assertThat(mine.answer().sources()).contains("proposal-9002", "cornell-1999");
        assertThat(theirs.answer()).isNull();
        assertThat(theirs.pending().decision().reason()).isEqualTo("pomodorino is not in the dataset");
    }

    @Test
    void pestRiskIsStillAnsweredWhenTheForecastIsMissing() {
        String month = java.time.LocalDate.now(GardenPipelineAgent.ITALY).getMonth().name();
        whenCreateObject(prompt -> prompt.contains("Question: What threatens my garden now?"), Intent.class)
                .thenReturn(new Intent(QueryKind.PEST_RISK, null, null, "en", null, month));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", "What threatens my garden now?"),
                withGarden("carl"));

        assertThat(run.answer()).isNotNull();
        assertThat(run.answer().notes()).singleElement().asString().contains("not available");
        verifyGenerateText(prompt -> prompt.contains("Notes: The weather forecast is not available"));
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
    void antagonistsAreWhatNotToPlantNearby() {
        whenCreateObject(prompt -> prompt.contains("Question: What should I not plant next to tomato?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.ANTAGONISTS, "tomato"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(
                new Question("ask", "What should I not plant next to tomato?"), signedIn("quentin"));

        assertThat(run.answer().answer()).contains("Potato", "Florence fennel", "Cabbage").doesNotContain("Basil");
        assertThat(run.answer().sources()).contains("purdue-bp80");
    }

    @Test
    void sowingTimeDefaultsToTheCentreAndUsesInferredWindowsElsewhere() {
        whenCreateObject(prompt -> prompt.contains("Question: When do I sow tomato?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.SOWING_TIME, "tomato"));
        whenCreateObject(prompt -> prompt.contains("Question: When do I sow tomato in the north?"), Intent.class)
                .thenReturn(new Intent(QueryKind.SOWING_TIME, "tomato", null, "en", "NORD", null));

        GardenPipeline.Run centre = pipeline.askKnowledgeBase(new Question("ask", "When do I sow tomato?"), signedIn("rosa"));
        GardenPipeline.Run north = pipeline.askKnowledgeBase(
                new Question("ask", "When do I sow tomato in the north?"), signedIn("rosa"));

        assertThat(centre.answer().answer()).containsExactlyInAnyOrder("FEBRUARY", "MARCH", "APRIL", "MAY");
        assertThat(north.answer().answer()).containsExactlyInAnyOrder("MARCH", "APRIL", "MAY", "JUNE");
    }

    @Test
    void sowNowListsTheCropsOfTheMonthInTheQuestionsLanguage() {
        whenCreateObject(prompt -> prompt.contains("Question: Cosa posso seminare a marzo?"), Intent.class)
                .thenReturn(new Intent(QueryKind.SOW_NOW, null, null, "it", null, "MARCH"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", "Cosa posso seminare a marzo?"),
                signedIn("sara"));

        assertThat(run.answer().answer()).contains("Pomodoro", "Patata", "Basilico").doesNotContain("Zucchina");
    }

    @Test
    void defaultsGiveTheCentreAndTheCurrentMonth() {
        Intent now = GardenPipelineAgent.withDefaults(new Intent(QueryKind.SOW_NOW, null, null, "it", null, null));

        assertThat(now.zone()).isEqualTo("CENTRO");
        assertThat(now.month()).isEqualTo(java.time.LocalDate.now(GardenPipelineAgent.ITALY).getMonth().name());
        assertThat(GardenPipelineAgent.withDefaults(Intent.of(QueryKind.COMPANIONS, "Tomato")))
                .isEqualTo(Intent.of(QueryKind.COMPANIONS, "Tomato"));
    }

    @Test
    void growingNowListsTheSpacesCropsInGround() {
        whenCreateObject(prompt -> prompt.contains("Question: What do I grow now?"), Intent.class)
                .thenReturn(new Intent(QueryKind.GROWING_NOW, null, null, "en"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", "What do I grow now?"), withGarden("tina"));

        assertThat(run.answer().answer()).containsExactly("Tomato");
        assertThat(run.answer().sources()).containsExactly("garden");
    }

    @Test
    void pestRiskIsWhatThreatensWhatGrowsInTheMonth() {
        whenCreateObject(prompt -> prompt.contains("Question: Which pests threaten my garden in August?"), Intent.class)
                .thenReturn(new Intent(QueryKind.PEST_RISK, null, null, "en", null, "AUGUST"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(
                new Question("ask", "Which pests threaten my garden in August?"), withGarden("ugo"));

        assertThat(run.answer().answer()).contains("Late blight", "Tomato leafminer").doesNotContain("Carrot fly");
        assertThat(run.answer().sources()).contains("eppo-codes");
    }

    @Test
    void sowInPlotUsesTheGardensZoneAndSkipsCropsAgainstRotation() {
        whenCreateObject(prompt -> prompt.contains("Question: What can I sow in plot B in March?"), Intent.class)
                .thenReturn(new Intent(QueryKind.SOW_IN_PLOT, "B", null, "en", null, "MARCH"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(
                new Question("ask", "What can I sow in plot B in March?"), withGarden("vera"));

        // Lettuce is sowable in March in the north; tomato and eggplant would follow the tomato (Solanaceae);
        // basil starts in March in the centre but in April in the north, so the garden's zone was used.
        assertThat(run.answer().answer()).contains("Lettuce").doesNotContain("Tomato", "Eggplant", "Basil");
    }

    @Test
    void anUnknownPlotWaitsForAPerson() {
        whenCreateObject(prompt -> prompt.contains("Question: What can I sow in plot Z?"), Intent.class)
                .thenReturn(new Intent(QueryKind.SOW_IN_PLOT, "Z", null, "en", null, "MARCH"));

        GardenPipeline.Run run = pipeline.askKnowledgeBase(new Question("ask", "What can I sow in plot Z?"),
                withGarden("walt"));

        assertThat(run.answer()).isNull();
    }

    @Test
    void unsupportedQuestionIsEscalated() {
        var answer = ask("other", "What is the best fertilizer?", Intent.of(QueryKind.OTHER, null));

        assertThat(answer.escalated()).isTrue();
    }
}
