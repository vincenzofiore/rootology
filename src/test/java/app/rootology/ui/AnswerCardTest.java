package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The answer card's values (#110): in the page's language, and never a badge for a word just read. */
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
