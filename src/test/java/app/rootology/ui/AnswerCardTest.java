package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import app.rootology.pipeline.ContextSnapshot;
import app.rootology.pipeline.ContextSnapshot.ClimateFrom;
import app.rootology.pipeline.ContextSnapshot.MonthFrom;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The answer card's values (#110): in the page's language, and never a badge for a word just read. And its
 * fold (#111): what it is called on an answer and on a waiting question, what it says about the reliability,
 * and that it never repeats the line above the answer.
 */
class AnswerCardTest {

    @Test
    void valuesTheSentenceSaysStayOutAndTheOthersAreShown() {
        AnswerCard card = new AnswerCard()
                .answered("Puoi seminare da agosto a settembre.", List.of("AUGUST", "SEPTEMBER", "Basilico"), 0.9);

        assertThat(chips(card)).containsExactly("Basilico");
    }

    @Test
    void theValuesAreWordedInThePagesLanguage() {
        AnswerCard card = new AnswerCard().answered("Ecco quando.", List.of("AUGUST", "Basilico"), 0.9);

        assertThat(chips(card)).containsExactly("agosto", "Basilico");
    }

    @Test
    void noBadgesRowAtAllWhenEveryValueIsInTheSentence() {
        AnswerCard card = new AnswerCard()
                .answered("Puoi seminare da agosto a settembre.", List.of("AUGUST", "SEPTEMBER"), 0.9);

        assertThat(chips(card)).isEmpty();
        assertThat(nodes(card)).noneMatch(c -> c.getClassNames().contains("rootology-answer-values"));
    }

    @Test
    void anAnswerWithoutValuesKeepsItsNoValuesBadge() {
        AnswerCard card = new AnswerCard().answered("Nessuna compagna dichiarata.", List.of(), 0.9);

        assertThat(nodes(card)).anyMatch(c -> c.getClassNames().contains("rootology-answer-value-none"));
    }

    @Test
    void aCalendarGroupsItsMonthsByHowTheyGoIntoTheGround() {
        // #112: a seedbed month never reads as a month to plant out, even when the sentence names it.
        java.util.Map<String, List<String>> activities = new java.util.LinkedHashMap<>();
        activities.put("FEBRUARY", List.of("SOW_SEEDBED"));
        activities.put("MARCH", List.of("SOW_SEEDBED"));
        activities.put("APRIL", List.of("TRANSPLANT"));
        AnswerCard card = new AnswerCard().answered("In semenzaio a febbraio e marzo, trapianto ad aprile.",
                List.of("FEBRUARY", "MARCH", "APRIL"), 0.9, activities);

        List<String> rows = nodes(card).stream().filter(c -> c.getClassNames().contains("rootology-answer-values"))
                .map(row -> String.join(" ", row.getChildren().map(c -> ((HasText) c).getText()).toList()))
                .toList();
        assertThat(rows).containsExactly("!{activity.SOW_SEEDBED}! febbraio marzo", "!{activity.TRANSPLANT}! aprile");
    }

    @Test
    void anAnswerAPersonGaveSaysWhoAndHasNoConfidenceBar() {
        // #116: the bar measures the decision layer, which did not answer.
        AnswerCard card = new AnswerCard().answeredBy("Sì, a maggio.", List.of("MAY"), "vincenzo", java.util.Map.of());

        assertThat(nodes(card)).noneMatch(c -> c instanceof com.vaadin.flow.component.progressbar.ProgressBar);
        assertThat(nodes(card)).anyMatch(c -> c.getClassNames().contains("rootology-answer-person"));
        assertThat(foldLabels(card)).isEmpty();
    }

    @Test
    void anAnswersFoldAsksWhyItAnswersAWaitingQuestionsWhyItWaits() {
        AnswerCard answer = new AnswerCard().answered("Ecco quando.", List.of(), 0.9);
        AnswerCard waiting = new AnswerCard().promise("Jev: the facts do not answer this (p=0.08)");

        assertThat(foldLabels(answer)).containsExactly("card.why");
        assertThat(foldLabels(waiting)).containsExactly("card.why.waiting");
    }

    @Test
    void theFoldSaysWhatTheReliabilityMeans() {
        AnswerCard card = new AnswerCard().answered("Ecco quando.", List.of(), 0.9);

        assertThat(foldedText(card)).contains("card.confidence.meaning");
    }

    @Test
    void theFoldDoesNotRepeatTheGardenTheAreaAndTheMonthAboveAnAnswer() {
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", null, null, "NOVEMBER",
                MonthFrom.DEFAULT, "Aiuola B", 90, 320, ClimateFrom.GARDEN);

        AnswerCard card = new AnswerCard().context(context).answered("Ecco quando.", List.of(), 0.9);

        assertThat(foldedText(card))
                .as("only the climate and its source, which the line above does not say")
                .contains("card.why.context", "context.climate.garden")
                .doesNotContain("context.for", "context.place", "context.month", "Aiuola B");
    }

    @Test
    void theFoldSaysNothingAboutTheContextWhenTheLineAboveSaysItAll() {
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", null, null, "NOVEMBER",
                MonthFrom.DEFAULT, null);

        AnswerCard card = new AnswerCard().context(context).answered("Ecco quando.", List.of(), 0.9);

        assertThat(foldedText(card)).doesNotContain("context.for", "context.climate", "context.zone", "context.month");
    }

    @Test
    void aMonthTheQuestionNamedIsSaidInTheFold() {
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", null, null, "MARCH",
                MonthFrom.QUESTION, null);

        AnswerCard card = new AnswerCard().context(context).answered("Ecco quando.", List.of(), 0.9);

        assertThat(foldedText(card)).contains("context.month.question");
    }

    @Test
    void aPastAnswerSavedBeforeSourcesSaysSoAndAFreshOneStaysSilent() {
        AnswerCard past = new AnswerCard().noSources();
        AnswerCard fresh = new AnswerCard().sources(List.of());

        assertThat(foldedText(past)).contains("ask.sources", "ask.sources.before");
        assertThat(foldLabels(fresh)).isEmpty();
    }

    /** The labels of the card's folds, without Vaadin's marker for a key the test has no translation of. */
    private static List<String> foldLabels(Component root) {
        return nodes(root).stream().filter(Details.class::isInstance).map(c -> ((Details) c).getSummaryText())
                .map(label -> label.replaceFirst("^!\\{", "").replaceFirst("}!$", "")).toList();
    }

    /** The text inside the card's folds, and nothing else. */
    private static String foldedText(Component root) {
        StringBuilder all = new StringBuilder();
        nodes(root).stream().filter(Details.class::isInstance).forEach(d -> {
            StringBuilder inside = new StringBuilder();
            appendText(d, inside);
            all.append(' ').append(inside);
        });
        return all.toString();
    }

    private static void appendText(Component root, StringBuilder all) {
        if (root instanceof HasText hasText) {
            all.append(' ').append(hasText.getText());
        }
        root.getChildren().forEach(child -> appendText(child, all));
    }

    /** The words of the values row. */
    private static List<String> chips(Component root) {
        return nodes(root).stream().filter(c -> c.getClassNames().contains("rootology-answer-value"))
                .filter(Span.class::isInstance).map(c -> ((Span) c).getText()).toList();
    }

    /** Every component of the card, the card itself included. */
    private static List<Component> nodes(Component root) {
        List<Component> all = new ArrayList<>();
        collect(root, all);
        return all;
    }

    private static void collect(Component root, List<Component> all) {
        all.add(root);
        if (root instanceof HasText) {
            root.getChildren().forEach(child -> collect(child, all));
        }
    }
}
