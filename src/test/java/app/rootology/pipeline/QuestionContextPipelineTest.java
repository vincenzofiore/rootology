package app.rootology.pipeline;

import com.embabel.agent.test.integration.EmbabelMockitoIntegrationTest;
import app.rootology.TestcontainersConfiguration;
import app.rootology.decision.DecisionLayer;
import app.rootology.decision.Intent;
import app.rootology.decision.Note;
import app.rootology.decision.QueryKind;
import app.rootology.garden.GardenService;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.history.RunHistory;
import app.rootology.spaces.SpaceService;
import app.rootology.weather.WeatherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The context of a question reaches the facts, the forecast, the answer and the history (#55): only the
 * run's garden is read, ambiguity escalates before the decision layer, and what was used is stored.
 */
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "rootology.jev.api-key=")
class QuestionContextPipelineTest extends EmbabelMockitoIntegrationTest {

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    RunHistory history;

    @MockitoBean
    WeatherService weather;

    /** The real stub, watched: an escalation the context decides must not reach the decision layer. */
    @MockitoSpyBean
    DecisionLayer decisionLayer;

    @org.junit.jupiter.api.BeforeEach
    void stubPhrasing() {
        whenGenerateText(prompt -> prompt.contains("Write a one or two sentence answer"))
                .thenReturn("phrased answer");
    }

    private long spaceOf(String login) {
        spaces.signedIn(login);
        return spaces.spacesOf(login).getFirst().id();
    }

    private long garden(long space, String login, String name, String latitude, String longitude) {
        return gardens.saveGarden(space, login, null, name, null,
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude)).id();
    }

    private void plant(long space, String login, long garden, String crop) {
        var plot = gardens.savePlot(space, login, garden, null, "Aiuola A", PlotKind.GROUND, Soil.LOAMY, null);
        gardens.plant(space, login, plot.id(), crop, PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 9, 1), null);
    }

    private GardenPipeline.Run ask(String login, long space, long garden, String text, Intent intent) {
        whenCreateObject(prompt -> prompt.contains("Question: " + text + "\n"), Intent.class).thenReturn(intent);
        return pipeline.askKnowledgeBase(new Question("ask", text), new RunContext(space, garden, login));
    }

    @Test
    void everyFactAndTheZoneComeFromTheRunsGardenOnly() {
        long space = spaceOf("ctx-two");
        long north = garden(space, "ctx-two", "Orto di Milano", "45.46", "9.19");
        long south = garden(space, "ctx-two", "Orto di Palermo", "38.12", "13.36");
        plant(space, "ctx-two", north, "tomato");
        plant(space, "ctx-two", south, "lettuce");

        var grownNorth = ask("ctx-two", space, north, "What do I grow now?",
                new Intent(QueryKind.GROWING_NOW, null, null, "en"));
        var grownSouth = ask("ctx-two", space, south, "What do I grow now?",
                new Intent(QueryKind.GROWING_NOW, null, null, "en"));
        var sownNorth = ask("ctx-two", space, north, "When do I sow tomato?",
                new Intent(QueryKind.SOWING_TIME, "tomato", null, "en"));
        var sownSouth = ask("ctx-two", space, south, "When do I sow tomato?",
                new Intent(QueryKind.SOWING_TIME, "tomato", null, "en"));

        // Only that garden's plantings, never both: the two answers of one space are different facts.
        assertThat(grownNorth.answer().answer()).containsExactly("Tomato");
        assertThat(grownSouth.answer().answer()).containsExactly("Lettuce");
        assertThat(grownNorth.answer().context().gardenName()).isEqualTo("Orto di Milano");
        // Each garden's own climate profile, and so its calendar; both are in the same cell as Vasto's days.
        assertThat(sownNorth.answer().context().zone()).isNull();
        assertThat(sownNorth.answer().context().climateFrom()).isEqualTo(ContextSnapshot.ClimateFrom.GARDEN);
        assertThat(sownNorth.answer().answer()).containsExactlyInAnyOrder("FEBRUARY", "MARCH", "APRIL", "MAY");
        assertThat(sownSouth.answer().context().climateFrom()).isEqualTo(ContextSnapshot.ClimateFrom.GARDEN);
        assertThat(sownSouth.answer().answer()).isEqualTo(sownNorth.answer().answer());
    }

    @Test
    void theForecastIsTheRunsGardenAndTheQuestionWinsOnTheZone() {
        long space = spaceOf("ctx-weather");
        long north = garden(space, "ctx-weather", "Orto di Milano", "45.46", "9.19");
        org.mockito.Mockito.when(weather.around(any(), any())).thenReturn(java.util.Optional.empty());

        var centre = ask("ctx-weather", space, north, "Cosa semino adesso?",
                new Intent(QueryKind.SOW_NOW, null, null, "it"));
        var south = ask("ctx-weather", space, north, "Cosa semino adesso al sud?",
                new Intent(QueryKind.SOW_NOW, null, null, "it", "SUD", null));

        // The forecast is the run's garden's; a question naming another zone keeps it and says so.
        verify(weather, org.mockito.Mockito.times(2)).around(new BigDecimal("45.46"), new BigDecimal("9.19"));
        assertThat(centre.answer().context().zone()).isNull();
        assertThat(centre.answer().context().climateFrom()).isEqualTo(ContextSnapshot.ClimateFrom.GARDEN);
        assertThat(south.answer().context().zone()).isEqualTo("SUD");
        assertThat(south.answer().context().zoneFrom()).isEqualTo(ContextSnapshot.ZoneFrom.QUESTION);
        assertThat(south.answer().context().climateFrom()).isEqualTo(ContextSnapshot.ClimateFrom.QUESTION);
        assertThat(south.answer().notes()).extracting(Note::kind).contains(Note.Kind.ZONE_OF_QUESTION);
    }

    @Test
    void aGeneralQuestionAtTheSpaceLevelUsesTheDefaultsAndSaysNoGardenWasUsed() {
        long space = spaceOf("ctx-space");
        garden(space, "ctx-space", "Orto di Milano", "45.46", "9.19");

        var run = ask("ctx-space", space, 0, "Cosa posso seminare adesso?",
                new Intent(QueryKind.SOW_NOW, null, null, "it"));

        assertThat(run.answer()).isNotNull();
        assertThat(run.answer().context().gardenName()).isNull();
        assertThat(run.answer().context().zone()).isNull();
        assertThat(run.answer().context().climateFrom()).isEqualTo(ContextSnapshot.ClimateFrom.DEFAULT);
        assertThat(run.answer().notes()).extracting(Note::kind).contains(Note.Kind.NO_GARDEN_DEFAULTS);
        org.mockito.Mockito.verifyNoInteractions(weather);
    }

    @Test
    void aQuestionAboutTheGardenAtTheSpaceLevelEscalatesWithoutTheDecisionLayer() {
        long space = spaceOf("ctx-needsgarden");
        plant(space, "ctx-needsgarden", garden(space, "ctx-needsgarden", "Orto", "45.46", "9.19"), "tomato");

        var run = ask("ctx-needsgarden", space, 0, "Cosa cresce nel mio orto?",
                new Intent(QueryKind.GROWING_NOW, null, null, "it"));

        assertThat(run.answer()).isNull();
        assertThat(run.pending().decision().reason()).isEqualTo(ContextResolver.NEEDS_GARDEN_REASON);
        assertThat(run.pending().decision().escalate()).isTrue();
        verify(decisionLayer, never()).decide(any(), any());
    }

    @Test
    void aPlotNotFoundOrMatchingSeveralEscalatesWithANoteAndNoDecision() {
        long space = spaceOf("ctx-plots");
        long garden = garden(space, "ctx-plots", "Orto", "45.46", "9.19");
        gardens.savePlot(space, "ctx-plots", garden, null, "Aiuola A", PlotKind.GROUND, Soil.LOAMY, null);
        gardens.savePlot(space, "ctx-plots", garden, null, "Aiuola B", PlotKind.GROUND, Soil.LOAMY, null);

        var missing = ask("ctx-plots", space, garden, "Cosa posso seminare nell'aiuola Z?",
                new Intent(QueryKind.SOW_IN_PLOT, "aiuola Z", null, "it"));
        var ambiguous = ask("ctx-plots", space, garden, "Cosa posso seminare nell'aiuola?",
                new Intent(QueryKind.SOW_IN_PLOT, "aiuola", null, "it"));

        assertThat(missing.pending().decision().reason()).isEqualTo(ContextResolver.PLOT_REASON);
        assertThat(missing.pending().decision().escalate()).isTrue();
        assertThat(ambiguous.pending().decision().reason()).isEqualTo(ContextResolver.PLOT_REASON);
        verify(decisionLayer, never()).decide(any(), any());
    }

    @Test
    void aGardenDeletedBetweenThePageAndTheQuestionEscalates() {
        long space = spaceOf("ctx-gone");
        long garden = garden(space, "ctx-gone", "Orto sparito", "45.46", "9.19");
        gardens.deleteGarden(space, "ctx-gone", garden);

        var run = ask("ctx-gone", space, garden, "Cosa cresce nel mio orto?",
                new Intent(QueryKind.GROWING_NOW, null, null, "it"));

        assertThat(run.answer()).isNull();
        assertThat(run.pending().decision().reason()).isEqualTo(ContextResolver.GARDEN_GONE_REASON);
        verify(decisionLayer, never()).decide(any(), any());
    }

    @Test
    void anEscalationKeepsTheNotesOfItsQuestionAfterARestart() {
        long space = spaceOf("ctx-notes");
        long garden = garden(space, "ctx-notes", "Orto", "45.46", "9.19");
        whenGenerateText(prompt -> prompt.contains("Say that a human reviewer answered it")).thenReturn("answered");

        var asked = ask("ctx-notes", space, garden, "Cosa posso seminare nell'aiuola Z?",
                new Intent(QueryKind.SOW_IN_PLOT, "aiuola Z", null, "it"));
        assertThat(asked.pending().notes()).as("whoever answers sees why it was not answered")
                .extracting(Note::kind).contains(Note.Kind.PLOT_NOT_FOUND);

        assertThat(pipeline.pending(space, garden, "ctx-notes")).singleElement()
                .satisfies(review -> assertThat(review.notes()).as("also when read back from the database")
                        .extracting(Note::kind).contains(Note.Kind.PLOT_NOT_FOUND));

        agentPlatform.killAgentProcess(asked.pending().processId()); // as after a restart
        var answer = pipeline.respond(asked.pending().processId(), space, garden,
                new HumanAnswer(java.util.List.of(), "Non lo so", "ctx-notes"));

        assertThat(answer.notes()).as("the resumed run keeps them").extracting(Note::kind)
                .contains(Note.Kind.PLOT_NOT_FOUND);
    }

    @Test
    void theContextOfTheQuestionIsStoredInTheHistory() {
        long space = spaceOf("ctx-history");
        long garden = garden(space, "ctx-history", "Orto sul tetto", "45.46", "9.19");
        plant(space, "ctx-history", garden, "tomato");

        var run = ask("ctx-history", space, garden, "Quando semino il pomodoro?",
                new Intent(QueryKind.SOWING_TIME, "pomodoro", null, "it"));

        var stored = history.recent(space, garden, "ctx-history").getFirst();
        assertThat(history.contextOf(stored)).hasValueSatisfying(context -> {
            assertThat(context.gardenName()).isEqualTo("Orto sul tetto");
            assertThat(context.climateFrom()).isEqualTo(ContextSnapshot.ClimateFrom.GARDEN);
        });
        assertThat(run.answer().context()).isEqualTo(history.contextOf(stored).orElseThrow());
    }

    @Test
    void anEscalationKeepsTheContextOfItsQuestionAfterARestart() {
        long space = spaceOf("ctx-restart");
        long garden = garden(space, "ctx-restart", "Orto", "45.46", "9.19");
        whenGenerateText(prompt -> prompt.contains("Say that a human reviewer answered it")).thenReturn("answered");

        var asked = ask("ctx-restart", space, garden, "Quando semino la lavanda?",
                new Intent(QueryKind.SOWING_TIME, "lavanda", null, "it"));
        assertThat(asked.pending().decision().reason()).contains("lavanda");
        assertThat(asked.pending().snapshot().gardenName()).as("whoever answers sees the context").isEqualTo("Orto");
        assertThat(pipeline.pending(space, garden, "ctx-restart")).singleElement()
                .satisfies(review -> assertThat(review.snapshot().climateFrom())
                        .as("also when read back from the database").isEqualTo(ContextSnapshot.ClimateFrom.GARDEN));

        agentPlatform.killAgentProcess(asked.pending().processId()); // as after a restart
        var answer = pipeline.respond(asked.pending().processId(), space, garden,
                new HumanAnswer(java.util.List.of(), "Non lo so", "ctx-restart"));

        assertThat(answer.context().gardenName()).isEqualTo("Orto");
        assertThat(answer.context().climateFrom()).isEqualTo(ContextSnapshot.ClimateFrom.GARDEN);
        assertThat(history.recent(space, garden, "ctx-restart").getFirst().getContextJson())
                .as("the resumed answer stores the context of the question")
                .isNotNull();
    }

    /** A question naming another garden (#57): Claude read the names, and the answer and its history are that garden's. */
    @Test
    void aQuestionNamingAnotherGardenIsAnsweredAndStoredForIt() {
        long space = spaceOf("ctx-other");
        long roof = garden(space, "ctx-other", "Orto sul tetto", "45.46", "9.19");
        long sea = garden(space, "ctx-other", "Orto sul mare", "38.12", "13.36");
        plant(space, "ctx-other", roof, "tomato");
        plant(space, "ctx-other", sea, "lettuce");
        String text = "Cosa cresce nell'orto sul mare?";
        whenCreateObject(prompt -> prompt.contains("Question: " + text + "\n")
                        && prompt.contains("\"Orto sul tetto\", \"Orto sul mare\"")
                        && prompt.contains("The plots of the current garden: \"Aiuola A\""), Intent.class)
                .thenReturn(new Intent(QueryKind.GROWING_NOW, null, null, "it", null, null, "orto sul mare"));

        var run = pipeline.askKnowledgeBase(new Question("ask", text), new RunContext(space, roof, "ctx-other"));

        assertThat(run.answer().answer()).containsExactly("Lattuga");
        assertThat(run.answer().notes()).extracting(Note::kind).contains(Note.Kind.GARDEN_OF_QUESTION);
        assertThat(run.answer().context().gardenName()).isEqualTo("Orto sul mare");
        assertThat(history.recent(space, sea, "ctx-other")).as("the history is the named garden's").hasSize(1);
        assertThat(history.recent(space, roof, "ctx-other")).isEmpty();
    }
}
