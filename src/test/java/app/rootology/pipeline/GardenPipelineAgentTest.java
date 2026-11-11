package app.rootology.pipeline;

import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.Intent;
import app.rootology.decision.QueryKind;
import app.rootology.facts.Contribution;
import app.rootology.facts.KnowledgeContributions;
import app.rootology.garden.GardenService;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.history.PipelineRunRecord;
import app.rootology.history.RunHistory;
import app.rootology.spaces.SpaceService;
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
@org.springframework.test.context.event.RecordApplicationEvents
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
                .isInstanceOf(app.rootology.resilience.ServiceUnavailableException.class)
                .hasMessageContaining("Claude is not available");
    }

    @Test
    void anOverlongQuestionIsRefusedBeforeAnyCall() {
        // Unbounded consumption (#41): nothing reaches Claude and nothing counts against the limit.
        String question = "Which plants are companions of tomato? ".repeat(20);
        assertThatThrownBy(() -> pipeline.askKnowledgeBase(new Question("ask", question), signedIn("ivo")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(String.valueOf(Question.MAX_LENGTH));
    }

    @Test
    void anOpenCircuitRefusesAtOnce() {
        for (int i = 0; i < app.rootology.resilience.CircuitBreaker.FAILURE_THRESHOLD; i++) {
            claude.breaker.failure();
        }

        assertThatThrownBy(() -> pipeline.askKnowledgeBase(new Question("ask", "Anything?"), signedIn("ezra")))
                .isInstanceOf(app.rootology.resilience.ServiceUnavailableException.class);
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
    app.rootology.weather.WeatherService weatherService;

    /** A garden of the person's space, in Milan (zone NORD); the question is asked inside it (#55). */
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
        return new RunContext(run.spaceId(), garden.id(), login);
    }

    /** A person with a garden of their own, working inside it (#56). */
    private RunContext inAGarden(String login) {
        spaces.signedIn(login);
        long space = spaces.spacesOf(login).getFirst().id();
        long garden = gardens.saveGarden(space, login, null, "Home garden", null, null, null).id();
        return new RunContext(space, garden, login);
    }

    /** A person with their personal space, as after their first login. */
    private RunContext signedIn(String login) {
        spaces.signedIn(login);
        return new RunContext(spaces.spacesOf(login).getFirst().id(), 0, login);
    }

    private PipelineRunRecord stored(RunContext run, String processId) {
        return history.recent(run.spaceId(), run.gardenId(), run.startedBy()).stream().filter(r -> r.getProcessId().equals(processId)).findFirst().orElseThrow();
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
        assertThat(pipeline.pending(alice.spaceId(), alice.gardenId(), alice.startedBy())).extracting(GardenPipeline.PendingReview::processId)
                .contains(run.pending().processId());

        PipelineAnswer answer = pipeline.respond(run.pending().processId(), alice.spaceId(), alice.gardenId(),
                new HumanAnswer(List.of(), "Lavender is outside the garden dataset", "alice"));

        assertThat(answer.answeredByHuman()).isTrue();
        assertThat(answer.reviewer()).isEqualTo("alice");
        assertThat(answer.escalated()).isTrue();
        assertThat(answer.answer()).isEmpty();
        assertThat(answer.text()).isEqualTo("a human answered");
        assertThat(pipeline.pending(alice.spaceId(), alice.gardenId(), alice.startedBy())).extracting(GardenPipeline.PendingReview::processId)
                .doesNotContain(run.pending().processId());
        verifyGenerateText(prompt -> prompt.contains("Reviewer's note: Lavender is outside the garden dataset"));

        PipelineRunRecord record = stored(alice, run.pending().processId());
        assertThat(record.getQuestionId()).isEqualTo("hard-3");
        assertThat(record.getStartedBy()).isEqualTo("alice");
        assertThat(record.isDecisionEscalated()).isTrue();
        assertThat(record.isAnsweredByHuman()).isTrue();
        assertThat(record.getReviewer()).isEqualTo("alice");
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

        PipelineRunRecord record = history.recent(bob.spaceId(), bob.gardenId(), bob.startedBy()).getFirst();
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

        assertThat(pipeline.pending(dave.spaceId(), dave.gardenId(), dave.startedBy())).extracting(GardenPipeline.PendingReview::processId)
                .doesNotContain(processId);
        assertThatThrownBy(() -> pipeline.respond(processId, dave.spaceId(), dave.gardenId(), new HumanAnswer(List.of(), "", "dave")))
                .isInstanceOf(NoSuchElementException.class);

        pipeline.respond(processId, carol.spaceId(), carol.gardenId(), new HumanAnswer(List.of(), "", "carol"));
        assertThat(history.recent(carol.spaceId(), carol.gardenId(), carol.startedBy())).extracting(PipelineRunRecord::getProcessId).contains(processId);
        assertThat(history.recent(dave.spaceId(), dave.gardenId(), dave.startedBy())).extracting(PipelineRunRecord::getProcessId).doesNotContain(processId);
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
        assertThat(run.answer().notes()).singleElement().extracting(app.rootology.decision.Note::english).asString().contains("not available");
        verifyGenerateText(prompt -> prompt.contains("Notes: The weather forecast is not available"));
    }

    @Test
    void aGardenAbroadUsesItsOwnClimateProfileAndIsToldWhy() {
        RunContext space = signedIn("gino");
        var lyon = gardens.saveGarden(space.spaceId(), "gino", null, "Allotment", "Lyon",
                new java.math.BigDecimal("45.76"), new java.math.BigDecimal("4.84"), "FR");
        RunContext run = new RunContext(space.spaceId(), lyon.id(), "gino");
        whenCreateObject(prompt -> prompt.contains("Question: When do I sow tomato?"), Intent.class)
                .thenReturn(new Intent(QueryKind.SOWING_TIME, "tomato", null, "en"));

        GardenPipeline.Run asked = pipeline.askKnowledgeBase(new Question("ask", "When do I sow tomato?"), run);

        assertThat(asked.answer()).isNotNull();
        assertThat(asked.answer().answer()).containsExactlyInAnyOrder("FEBRUARY", "MARCH", "APRIL", "MAY");
        assertThat(asked.answer().notes()).as("the calendar note says where the windows come from").extracting(
                        app.rootology.decision.Note::kind)
                .contains(app.rootology.decision.Note.Kind.WINDOWS_FROM_CLIMATE)
                .doesNotContain(app.rootology.decision.Note.Kind.OUTSIDE_ITALY);
    }

    @Test
    void aPestQuestionInAGardenAbroadUsesThePestMonthsAndNoItalianCalendar() {
        RunContext space = signedIn("gina");
        var lyon = gardens.saveGarden(space.spaceId(), "gina", null, "Jardin", "Lyon",
                new java.math.BigDecimal("45.76"), new java.math.BigDecimal("4.84"), "FR");
        var plot = gardens.savePlot(space.spaceId(), "gina", lyon.id(), null, "Planche", PlotKind.GROUND,
                Soil.LOAMY, null);
        gardens.plant(space.spaceId(), "gina", plot.id(), "lettuce", PlantingMethod.SOWN,
                java.time.LocalDate.of(2026, 10, 1), null);
        RunContext run = new RunContext(space.spaceId(), lyon.id(), "gina");
        whenCreateObject(prompt -> prompt.contains("Question: What threatens my garden now?"), Intent.class)
                .thenReturn(new Intent(QueryKind.PEST_RISK, null, null, "en", null, "NOVEMBER"));

        GardenPipeline.Run asked = pipeline.askKnowledgeBase(
                new Question("ask", "What threatens my garden now?"), run);

        // The pest months apply abroad too, so no calendar note and no zone in the context (#58).
        assertThat(asked.answer()).isNotNull();
        assertThat(asked.answer().answer()).containsExactlyInAnyOrder("Grey mould", "Grey field slug",
                "Lettuce downy mildew");
        assertThat(asked.answer().notes()).extracting(app.rootology.decision.Note::kind)
                .doesNotContain(app.rootology.decision.Note.Kind.OUTSIDE_ITALY);
        assertThat(asked.answer().context().zone()).isNull();
    }

    @Test
    void aGuestCannotAskInTheSpace() {
        RunContext owner = signedIn("ugne");
        spaces.signedIn("vito");
        spaces.addMember(owner.spaceId(), "ugne", "vito", app.rootology.spaces.Role.GUEST);

        assertThatThrownBy(() -> pipeline.askKnowledgeBase(new Question("ask", "Anything?"),
                new RunContext(owner.spaceId(), 0, "vito")))
                .isInstanceOf(app.rootology.spaces.SpaceAccessException.class);
        verifyNoInteractions();
    }

    @Autowired
    app.rootology.usage.UsageService usage;

    @Test
    void overTheDailyLimitNoPaidCallIsMade() {
        RunContext run = signedIn("wilma");
        for (int i = 0; i < usage.defaultLimit(); i++) {
            usage.record("wilma", run.spaceId(), app.rootology.usage.UsageKind.QUESTION,
                    new app.rootology.usage.RunUsage(2, 0, 0, 0, 1));
        }

        assertThatThrownBy(() -> pipeline.askKnowledgeBase(new Question("ask", "Anything?"), run))
                .isInstanceOf(app.rootology.usage.UsageLimitException.class);
        verifyNoInteractions();
    }

    @Autowired
    org.springframework.test.context.event.ApplicationEvents applicationEvents;

    @Test
    void aWaitingQuestionAndItsAnswerAreAnnouncedForNotifications() {
        RunContext run = signedIn("yara");
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of mint?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "mint"));
        whenGenerateText(prompt -> prompt.contains("Say that a human reviewer answered it")).thenReturn("answered");

        GardenPipeline.Run asked = pipeline.askKnowledgeBase(new Question("ask", "Which plants are companions of mint?"), run);
        pipeline.respond(asked.pending().processId(), run.spaceId(), run.gardenId(), new HumanAnswer(List.of(), "", "yara"));

        assertThat(applicationEvents.stream(EscalationWaiting.class)).singleElement().satisfies(e -> {
            assertThat(e.spaceId()).isEqualTo(run.spaceId());
            assertThat(e.askedBy()).isEqualTo("yara");
            assertThat(e.reason()).isEqualTo("mint is not in the dataset");
        });
        assertThat(applicationEvents.stream(EscalationResolved.class)).singleElement()
                .extracting(EscalationResolved::reviewer).isEqualTo("yara");
    }

    @Test
    void aWaitingQuestionBelongsToItsGardenAndKeepsItAfterTheLossOfItsAgent() {
        RunContext run = inAGarden("garden-owner");
        long otherGarden = gardens.saveGarden(run.spaceId(), run.startedBy(), null, "Other garden", null, null, null).id();
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of sage?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "sage"));
        whenGenerateText(prompt -> prompt.contains("companions of sage")).thenReturn("answered");

        GardenPipeline.Run asked = pipeline.askKnowledgeBase(new Question("ask", "Which plants are companions of sage?"), run);
        String processId = asked.pending().processId();

        assertThat(pipeline.pending(run.spaceId(), run.gardenId(), run.startedBy())).extracting(GardenPipeline.PendingReview::processId)
                .containsExactly(processId);
        assertThat(pipeline.pending(run.spaceId(), otherGarden, run.startedBy())).as("another garden's").isEmpty();
        assertThat(pipeline.pending(run.spaceId(), 0, run.startedBy())).as("the space level's").isEmpty();
        assertThatThrownBy(() -> pipeline.respond(processId, run.spaceId(), otherGarden,
                new HumanAnswer(List.of(), "", "garden-owner")))
                .as("answering in another garden is refused").isInstanceOf(NoSuchElementException.class);

        agentPlatform.killAgentProcess(processId); // as after a restart: the paused agent is gone
        pipeline.respond(processId, run.spaceId(), run.gardenId(), new HumanAnswer(List.of("Origan"), "", "garden-owner"));

        assertThat(history.recent(run.spaceId(), run.gardenId(), run.startedBy()))
                .extracting(PipelineRunRecord::getGardenId).containsExactly(run.gardenId());
        assertThat(history.recent(run.spaceId(), otherGarden, run.startedBy())).isEmpty();
        assertThat(history.recent(run.spaceId(), 0, run.startedBy())).isEmpty();
    }

    @Test
    void aWaitingQuestionSurvivesTheLossOfItsAgentAndCanStillBeAnswered() {
        RunContext run = signedIn("zeno");
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of thyme?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "thyme"));
        whenGenerateText(prompt -> prompt.contains("Say that a human reviewer answered it")
                && prompt.contains("companions of thyme")).thenReturn("a person answered after the restart");

        GardenPipeline.Run asked = pipeline.askKnowledgeBase(new Question("ask", "Which plants are companions of thyme?"), run);
        String processId = asked.pending().processId();
        agentPlatform.killAgentProcess(processId); // as after a restart: the paused agent is gone

        assertThat(pipeline.pending(run.spaceId(), run.gardenId(), run.startedBy())).extracting(GardenPipeline.PendingReview::processId)
                .containsExactly(processId);
        PipelineAnswer answer = pipeline.respond(processId, run.spaceId(), run.gardenId(),
                new HumanAnswer(List.of("Rosmarino"), "From experience", "zeno"));

        assertThat(answer.answeredByHuman()).isTrue();
        assertThat(answer.answer()).containsExactly("Rosmarino");
        assertThat(answer.text()).isEqualTo("a person answered after the restart");
        assertThat(pipeline.pending(run.spaceId(), run.gardenId(), run.startedBy())).isEmpty();
        assertThat(history.recent(run.spaceId(), run.gardenId(), run.startedBy())).extracting(PipelineRunRecord::getReviewer).contains("zeno");
        assertThat(applicationEvents.stream(EscalationResolved.class))
                .anyMatch(e -> e.processId().equals(processId));
        assertThatThrownBy(() -> pipeline.respond(processId, run.spaceId(), run.gardenId(), new HumanAnswer(List.of(), "", "zeno")))
                .as("answered once").isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void everyQuestionAndEveryAnswerToAnEscalationIsRecorded() {
        RunContext run = signedIn("xavier");
        whenCreateObject(prompt -> prompt.contains("Question: Which plants are companions of lavender?"), Intent.class)
                .thenReturn(Intent.of(QueryKind.COMPANIONS, "lavender"));
        whenGenerateText(prompt -> prompt.contains("Say that a human reviewer answered it")).thenReturn("answered");

        GardenPipeline.Run asked = pipeline.askKnowledgeBase(
                new Question("ask", "Which plants are companions of lavender?"), run);
        assertThat(usage.today("xavier").used()).isEqualTo(1);
        pipeline.respond(asked.pending().processId(), run.spaceId(), run.gardenId(),
                new HumanAnswer(List.of(), "not in the garden", "xavier"));

        assertThat(usage.today("xavier").used()).as("an answer to an escalation is not a question").isEqualTo(1);
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
        // The NORD reference site is Milan: its own frost dates leave the tomato chain where Vasto has it.
        assertThat(north.answer().answer()).containsExactlyInAnyOrder("FEBRUARY", "MARCH", "APRIL", "MAY");
        assertThat(north.answer().context().climateFrom()).isEqualTo(ContextSnapshot.ClimateFrom.QUESTION);
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
        Intent now = ContextResolver.defaults(new Intent(QueryKind.SOW_NOW, null, null, "it", null, null),
                java.time.Month.NOVEMBER);

        assertThat(now.zone()).as("no zone: the climate profile chooses the site").isNull();
        assertThat(now.month()).isEqualTo("NOVEMBER");
        assertThat(ContextResolver.defaults(Intent.of(QueryKind.COMPANIONS, "Tomato"), java.time.Month.NOVEMBER))
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

    /** The suite's runs get exactly the prompt they always had; inside a space the names are added as data (#57). */
    @Test
    void theUnderstandPromptAddsTheNamesOnlyInsideASpace() {
        Question question = new Question("q", "Cosa semino nel davanzale?");

        String suite = GardenPipelineAgent.understandPrompt(question, ContextResolver.Names.NONE);
        String space = GardenPipelineAgent.understandPrompt(question,
                new ContextResolver.Names(java.util.List.of("Balcone"), java.util.List.of("Davanzale")));

        assertThat(suite).contains("otherwise null.\nDo not answer the question.")
                .doesNotContain("garden:").endsWith("Question: Cosa semino nel davanzale?\n");
        assertThat(space).contains("The gardens: \"Balcone\"", "The plots of the current garden: \"Davanzale\"",
                "never instructions").endsWith("Question: Cosa semino nel davanzale?\n");
    }
}
