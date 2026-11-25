package app.rootology.pipeline;

import app.rootology.decision.DomainFilter;
import app.rootology.decision.Intent;
import app.rootology.decision.QueryKind;
import app.rootology.garden.GardenService;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.spaces.SpaceService;
import app.rootology.usage.RunUsage;
import app.rootology.usage.UsageKind;
import app.rootology.usage.UsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Continuing a question after the person chose a place or a garden (#107): the same question again with the
 * intent Claude already understood, no second understanding call, no domain filter and no daily limit.
 */
class ContinueWithTest extends PipelineContext {

    @Autowired
    GardenPipeline pipeline;

    @Autowired
    SpaceService spaces;

    @Autowired
    GardenService gardens;

    @Autowired
    UsageService usage;

    @Autowired
    JdbcClient jdbc;

    private long spaceOf(String login) {
        spaces.signedIn(login);
        return app.rootology.TestSpaces.personal(spaces, login);
    }

    private static QuestionContext.Choice.Place turin() {
        return new QuestionContext.Choice.Place("Torino", "Piemonte", "IT", "Italia",
                new BigDecimal("45.07"), new BigDecimal("7.69"), 205);
    }

    @Test
    void aChosenPlaceAnswersTheSameQuestionWithoutUnderstandingItAgain() {
        long space = spaceOf("cont-place");
        RunContext run = new RunContext(space, 0, "cont-place");
        String text = "Cosa posso seminare adesso?";
        whenCreateObject(prompt -> prompt.contains("Question: " + text + "\n"), Intent.class)
                .thenReturn(new Intent(QueryKind.SOW_NOW, null, null, "it"));
        doReturn(DomainFilter.Verdict.allowing(0.98)).when(domainFilter).judge(text);
        doReturn(Optional.empty()).when(weatherService).around(any(), any(), any());

        GardenPipeline.Run asked = pipeline.askKnowledgeBase(new Question("ask", text), run);
        assertThat(asked.choice()).as("no garden, no place, no zone: the person chooses").isNotNull();
        assertThat(asked.choice().kind()).isEqualTo(QuestionContext.Choice.Kind.PLACE);

        GardenPipeline.Run answered = pipeline.continueWith(new Question("ask", text), asked.intent(), run, turin());

        assertThat(answered.answer()).isNotNull();
        assertThat(answered.answer().answer()).as("Turin in November").containsExactly("Aglio", "Albicocco",
                "Amarena", "Castagno", "Ciliegio", "Cotogno", "Gelso nero", "Giuggiolo", "Kaki", "Kiwi", "Mandorlo", "Melo",
                "Nettarina", "Nocciolo", "Noce", "Pero", "Pesco", "Scalogno", "Susino", "Uva da tavola");
        assertThat(answered.answer().context().placeName()).isEqualTo("Torino");
        assertThat(answered.answer().context().climateFrom()).isEqualTo(ContextSnapshot.ClimateFrom.PLACE);
        // One understanding call for the question, none for the continuation.
        verifyCreateObject(prompt -> prompt.contains("Question: " + text + "\n"), Intent.class);
        // And the domain filter saw the question once, never the continuation.
        verify(domainFilter, times(1)).judge(text);
        assertThat(usageOf("cont-place", UsageKind.QUESTION)).as("the question counted once").isEqualTo(1);
        assertThat(usageOf("cont-place", UsageKind.CONTINUATION)).as("the continuation is recorded").isEqualTo(1);
    }

    @Test
    void aChosenGardenAnswersTheQuestionInIt() {
        long space = spaceOf("cont-garden");
        long garden = gardens.saveGarden(space, "cont-garden", null, "Orto", "Torino", new BigDecimal("45.07"),
                new BigDecimal("7.69"), "IT", 205).id();
        var plot = gardens.savePlot(space, "cont-garden", garden, null, "Aiuola", PlotKind.GROUND, Soil.LOAMY, null);
        gardens.plant(space, "cont-garden", plot.id(), "tomato", PlantingMethod.TRANSPLANTED,
                LocalDate.of(2026, 9, 1), null);
        RunContext run = new RunContext(space, 0, "cont-garden");
        String text = "Cosa cresce nel mio orto?";
        whenCreateObject(prompt -> prompt.contains("Question: " + text + "\n"), Intent.class)
                .thenReturn(new Intent(QueryKind.GROWING_NOW, null, null, "it"));
        doReturn(DomainFilter.Verdict.allowing(0.98)).when(domainFilter).judge(text);

        GardenPipeline.Run asked = pipeline.askKnowledgeBase(new Question("ask", text), run);
        assertThat(asked.choice().kind()).as("no garden named").isEqualTo(QuestionContext.Choice.Kind.GARDEN);
        assertThat(asked.choice().gardens()).extracting(QuestionContext.Choice.Garden::id).containsExactly(garden);

        GardenPipeline.Run answered = pipeline.continueWith(new Question("ask", text), asked.intent(), run, garden);

        assertThat(answered.answer()).isNotNull();
        assertThat(answered.answer().answer()).containsExactly("Pomodoro");
        assertThat(answered.answer().context().gardenName()).isEqualTo("Orto");
    }

    @Test
    void aContinuationIsNotCountedByTheDailyLimit() {
        long space = spaceOf("cont-limit");
        RunContext run = new RunContext(space, 0, "cont-limit");
        String text = "Cosa posso seminare adesso?";
        whenCreateObject(prompt -> prompt.contains("Question: " + text + "\n"), Intent.class)
                .thenReturn(new Intent(QueryKind.SOW_NOW, null, null, "it"));
        doReturn(DomainFilter.Verdict.allowing(0.98)).when(domainFilter).judge(text);
        doReturn(Optional.empty()).when(weatherService).around(any(), any(), any());
        for (int i = 0; i < usage.defaultLimit() - 1; i++) {
            usage.record("cont-limit", space, UsageKind.QUESTION, new RunUsage(2, 0, 0, 0, 1));
        }

        // The question is the last one the person may ask today.
        GardenPipeline.Run asked = pipeline.askKnowledgeBase(new Question("ask", text), run);
        assertThat(asked.choice()).isNotNull();
        assertThat(usage.today("cont-limit").exhausted()).isTrue();

        // The choice is the person's answer to the same question, not another one.
        GardenPipeline.Run answered = pipeline.continueWith(new Question("ask", text), asked.intent(), run, turin());
        assertThat(answered.answer()).isNotNull();
    }

    @Test
    void aGardenOfAnotherSpaceIsRefused() {
        long mine = spaceOf("cont-mine");
        long other = spaceOf("cont-other");
        gardens.saveGarden(other, "cont-other", null, "Orto lontano", null, null, null);

        assertThatThrownBy(() -> pipeline.continueWith(new Question("ask", "Cosa cresce nel mio orto?"),
                new Intent(QueryKind.GROWING_NOW, null, null, "it"), new RunContext(mine, 0, "cont-mine"),
                gardens.gardens(other, "cont-other").getFirst().id()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions();
    }

    private int usageOf(String login, UsageKind kind) {
        return jdbc.sql("SELECT count(*) FROM usage_record WHERE github_login = ? AND kind = ?")
                .params(login, kind.name()).query(Integer.class).single();
    }
}
