package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import app.rootology.decision.Decision;
import app.rootology.decision.Note;
import app.rootology.pipeline.GardenPipeline.PendingReview;
import app.rootology.pipeline.Question;
import app.rootology.pipeline.RunContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A waiting question as Ask shows it to the person who asked (#110): a promise that a person will answer, the
 * decision layer's reason and number folded under "Why this answer?", and the answer form behind one button for
 * whoever may answer. Escalations keeps the reviewer's card with everything in view.
 */
class WaitingForAskerTest {

    private static final PendingReview REVIEW = new PendingReview("p-1", new RunContext(1, 0, "anna"),
            new Question("ask", "Posso piantare l’aglio a novembre?"),
            new Decision(List.of("Aglio"), 0.08, true, "Jev: the facts do not answer this (p=0.08)"),
            null, List.of(new Note(Note.Kind.FROST_UNKNOWN, List.of("Aglio"))), Instant.now());

    @Test
    void theAskerReadsAPromiseAndTheReasonIsFolded() {
        WaitingQuestionCard card = card(true);

        assertThat(visibleText(card)).contains("card.waiting.promise").doesNotContain("reason.noanswer");
        assertThat(foldedText(card)).contains("card.waiting.reason");
        assertThat(foldLabels(card)).as("a waiting question asks why it waits (#111)").containsExactly("card.why.waiting");
    }

    @Test
    void theFormOpensWithOneTapForWhoeverMayAnswer() {
        WaitingQuestionCard card = card(true);
        Div form = form(card);
        Button open = buttons(card).stream().filter(b -> b.getText().contains("escalations.answeryourself"))
                .findFirst().orElseThrow();

        assertThat(form.isVisible()).isFalse();
        open.click();
        assertThat(form.isVisible()).isTrue();
        assertThat(open.isVisible()).isFalse();
    }

    @Test
    void aGuestFollowsTheQuestionWithNoFormAndNoButton() {
        WaitingQuestionCard card = card(false);

        assertThat(form(card).isVisible()).isFalse();
        assertThat(buttons(card)).noneMatch(b -> b.getText().contains("escalations.answeryourself"));
    }

    @Test
    void onAskTheQuestionIsAboveTheCardNotInsideIt() {
        // Ask's item says when and what was asked above every card, answered or waiting (#113).
        assertThat(nodes(card(true))).noneMatch(c -> c.getClassNames().contains("rootology-waiting-question"));
        assertThat(nodes(new WaitingQuestionCard(REVIEW, true, List.of(), (v, n) -> null, a -> new Div())))
                .anyMatch(c -> c.getClassNames().contains("rootology-waiting-question"));
    }

    @Test
    void escalationsKeepsTheReviewersCardWithEverythingInView() {
        WaitingQuestionCard card = new WaitingQuestionCard(REVIEW, true, List.of(), (v, n) -> null, a -> new Div());

        assertThat(visibleText(card)).contains("card.waiting.why");
        assertThat(form(card).isVisible()).isTrue();
    }

    private static WaitingQuestionCard card(boolean mayAnswer) {
        return new WaitingQuestionCard(REVIEW, mayAnswer, List.of(), (v, n) -> null, a -> new Div(), false, true);
    }

    private static Div form(Component root) {
        return (Div) nodes(root).stream().filter(c -> c.getClassNames().contains("rootology-answer-form"))
                .findFirst().orElseThrow();
    }

    private static List<Button> buttons(Component root) {
        return nodes(root).stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    /** The text outside "Why this answer?". */
    private static String visibleText(Component root) {
        StringBuilder all = new StringBuilder();
        collect(root, all, false);
        return all.toString();
    }

    /** The labels of the card's folds, without Vaadin's marker for a key the test has no translation of. */
    private static List<String> foldLabels(Component root) {
        return nodes(root).stream().filter(Details.class::isInstance).map(d -> ((Details) d).getSummaryText())
                .map(label -> label.replaceFirst("^!\\{", "").replaceFirst("}!$", "")).toList();
    }

    private static String foldedText(Component root) {
        StringBuilder all = new StringBuilder();
        nodes(root).stream().filter(Details.class::isInstance).forEach(d -> collect(d, all, true));
        return all.toString();
    }

    private static void collect(Component root, StringBuilder all, boolean intoDetails) {
        if (root instanceof Details && !intoDetails) {
            return;
        }
        if (root instanceof HasText hasText) {
            all.append(' ').append(hasText.getText());
        }
        root.getChildren().forEach(child -> collect(child, all, intoDetails));
    }

    private static List<Component> nodes(Component root) {
        List<Component> all = new ArrayList<>();
        all.add(root);
        root.getChildren().forEach(child -> all.addAll(nodes(child)));
        return all;
    }
}
