package app.rootology.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The answer a waiting question's card builds (#86): proposed chips, typed values and an explicit "none"
 * become exactly what {@code GardenPipeline.respond} receives, and the suggestions come from the catalogue
 * without duplicates.
 */
class WaitingQuestionCardTest {

    @Test
    void proposedValuesAreKeptUnlessThePersonRemovesOne() {
        AnswerDraft draft = new AnswerDraft(List.of("Basilico", "Pomodoro"));

        draft.remove("Pomodoro");

        assertThat(draft.answer()).containsExactly("Basilico");
    }

    @Test
    void typedValuesAreSplitTrimmedAndDeduplicated() {
        AnswerDraft draft = new AnswerDraft(List.of("Basilico"));

        draft.add(" Pomodoro , Basilico,  , Prezzemolo ");

        assertThat(draft.answer()).containsExactly("Basilico", "Pomodoro", "Prezzemolo");
    }

    @Test
    void anExplicitNoneAnswersWithNoValueAtAll() {
        AnswerDraft draft = new AnswerDraft(List.of("Basilico", "Pomodoro"));
        draft.add("Prezzemolo");

        draft.none(true);

        assertThat(draft.answer()).isEmpty();
        // Taking "none" back restores the values the person had, nothing is lost.
        draft.none(false);
        assertThat(draft.answer()).containsExactly("Basilico", "Pomodoro", "Prezzemolo");
    }

    @Test
    void noProposedValuesAndNoTypedValueIsTheNoneAnswerToo() {
        AnswerDraft draft = new AnswerDraft(List.of());

        assertThat(draft.answer()).isEmpty();
    }

    @Test
    void suggestionsAreCropsAndOrganismsSortedWithoutDuplicates() {
        List<String> suggestions = WaitingQuestionCard.suggestions(
                Stream.of("Pomodoro", "Basilico", " "),
                Stream.of("Afide", "Pomodoro"));

        assertThat(suggestions).containsExactly("Afide", "Basilico", "Pomodoro");
    }
}
