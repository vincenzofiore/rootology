package app.rootology.ui;

import app.rootology.decision.Intent;
import app.rootology.decision.QueryKind;
import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourceCatalog;
import app.rootology.garden.GardenService;
import app.rootology.geocoding.GeoNames;
import app.rootology.pipeline.GardenPipeline;
import app.rootology.pipeline.PipelineAnswer;
import app.rootology.pipeline.QuestionContext.Choice;
import app.rootology.pipeline.RunContext;
import app.rootology.usage.UsageLimitException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A question of the garden home answered in its dialog (#178): the answer, an off-topic refusal with "Ask anyway",
 * the choice of a place, the daily limit, and where "Go to questions" leads. The pipeline is a stub and the
 * question runs on the calling thread.
 */
class QuestionDialogTest {

    private static final String ASKED = "Quando semino l’aglio?";

    private final GardenPipeline pipeline = mock(GardenPipeline.class);
    private final CurrentUser user = mock(CurrentUser.class);

    @Test
    void anAnswerShowsUnderTheQuestionAndGoToQuestionsOpensThatRun() {
        when(pipeline.askKnowledgeBase(any(), any(), eq(false))).thenReturn(answered());

        QuestionDialog dialog = dialog();

        assertThat(all(dialog.body()).filter(H4.class::isInstance).map(h -> ((H4) h).getText()))
                .as("the question stays above its answer").containsExactly(ASKED);
        assertThat(all(dialog.body())).anyMatch(AnswerCard.class::isInstance);
        assertThat(dialog.questionsQuery()).isEqualTo(Map.of(AskView.RUN, "p-9"));
    }

    @Test
    void anOffTopicQuestionIsRefusedAndAskAnywayRunsItAgainPastTheFilter() {
        when(pipeline.askKnowledgeBase(any(), any(), eq(false)))
                .thenReturn(new GardenPipeline.Run(null, null, null, true, null, null, null));
        when(pipeline.askKnowledgeBase(any(), any(), eq(true))).thenReturn(answered());

        QuestionDialog dialog = dialog();
        Button anyway = all(dialog.body()).filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> "!{ask.offdomain.anyway}!".equals(button.getText())).findFirst().orElseThrow();
        anyway.click();

        verify(pipeline).askKnowledgeBase(any(), any(), eq(true));
        assertThat(all(dialog.body())).anyMatch(AnswerCard.class::isInstance);
    }

    @Test
    void aQuestionThatNeedsAPlaceShowsTheChoiceInTheDialog() {
        Choice.Place torino = new Choice.Place("Torino", "Piemonte", "IT", "Italia", new BigDecimal("45.07"),
                new BigDecimal("7.69"), 239);
        when(pipeline.askKnowledgeBase(any(), any(), eq(false))).thenReturn(new GardenPipeline.Run(null, null,
                new Intent(QueryKind.SOWING_TIME, "garlic", null, "it"), false,
                new Choice(Choice.Kind.PLACE, Choice.Why.MISSING, List.of(torino), List.of()), "p-1", null));

        QuestionDialog dialog = dialog();

        ChoiceCard choice = all(dialog.body()).filter(ChoiceCard.class::isInstance).map(ChoiceCard.class::cast)
                .findFirst().orElseThrow();
        assertThat(choice.showing()).isTrue();
        assertThat(choice.chips()).extracting(Button::getText).contains("Torino, Piemonte");
        assertThat(dialog.questionsQuery()).as("nothing answered yet: the history")
                .isEqualTo(Map.of(AskView.HISTORY, ""));
    }

    @Test
    void theDailyLimitIsSaidInTheDialog() {
        when(pipeline.askKnowledgeBase(any(), any(), eq(false))).thenThrow(new UsageLimitException("limit", 20));

        QuestionDialog dialog = dialog();

        assertThat(all(dialog.body()).filter(Paragraph.class::isInstance).map(p -> ((Paragraph) p).getText()))
                .containsExactly("!{ask.limit}!");
    }

    private QuestionDialog dialog() {
        when(user.canContribute(1)).thenReturn(true);
        when(user.runContext(1, 2)).thenReturn(new RunContext(1, 2, "ada"));
        when(user.login()).thenReturn("ada");
        GeoNames noSearch = mock(GeoNames.class);
        QuestionDialogs dialogs = new QuestionDialogs(pipeline, mock(SourceCatalog.class),
                mock(KnowledgeEnrichment.class), user, mock(CropCatalog.class), mock(KnowledgeGraph.class),
                mock(GardenService.class), noSearch, mock(ClimateCells.class), Runnable::run);
        return dialogs.ask(1, 2, "Orto", ASKED);
    }

    private static GardenPipeline.Run answered() {
        PipelineAnswer answer = new PipelineAnswer("ask", List.of("Aglio"), 0.8, false, false, null,
                "Seminare l’aglio.", List.of(), List.of(), null);
        return new GardenPipeline.Run(answer, null, new Intent(QueryKind.SOWING_TIME, "garlic", null, "it"), false,
                null, "p-9", null);
    }

    private static Stream<Component> all(Component component) {
        return Stream.concat(Stream.of(component), component.getChildren().flatMap(QuestionDialogTest::all));
    }
}
