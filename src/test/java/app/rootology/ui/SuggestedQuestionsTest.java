package app.rootology.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** The suggested questions of the Ask page (#82): supported kinds only, by season, crops and plots. */
class SuggestedQuestionsTest {

    private final Translations translations = new Translations();
    private final NoteText.Texts italian = (key, args) -> translations.getTranslation(key, Locale.ITALIAN, args);
    private final NoteText.Texts english = (key, args) -> translations.getTranslation(key, Locale.ENGLISH, args);

    private static List<String> keys(List<SuggestedQuestions.Suggestion> chips) {
        return chips.stream().map(SuggestedQuestions.Suggestion::key).toList();
    }

    @Test
    void theSeasonChoosesTheCropOfTheGeneralQuestions() {
        assertThat(keys(SuggestedQuestions.of(4, false, List.of(), null)))
                .containsExactly("ask.suggest.sow", "ask.suggest.companions.spring", "ask.suggest.sowing.spring");
        assertThat(keys(SuggestedQuestions.of(11, false, List.of(), null)))
                .containsExactly("ask.suggest.sow", "ask.suggest.companions.autumn", "ask.suggest.sowing.autumn");
        assertThat(SuggestedQuestions.season(1)).isEqualTo("winter");
        assertThat(SuggestedQuestions.season(12)).isEqualTo("winter");
        assertThat(SuggestedQuestions.season(7)).isEqualTo("summer");
    }

    @Test
    void atTheSpaceLevelTheGardensCropsAndPlotsAreIgnored() {
        assertThat(keys(SuggestedQuestions.of(4, false, List.of("Pomodoro"), "Nord")))
                .containsExactly("ask.suggest.sow", "ask.suggest.companions.spring", "ask.suggest.sowing.spring");
    }

    @Test
    void inAGardenTheCropsGrowingAndAPlotAddTheirQuestionsUpToFive() {
        List<SuggestedQuestions.Suggestion> chips = SuggestedQuestions.of(6, true,
                List.of("Pomodoro", "Zucchina", "Basilico"), "Nord");

        assertThat(keys(chips)).containsExactly("ask.suggest.sow", "ask.suggest.crop.pests",
                "ask.suggest.crop.harvest", "ask.suggest.plot", "ask.suggest.companions.summer");
        assertThat(chips.get(1).text(italian)).isEqualTo("Quali parassiti minacciano Pomodoro questo mese?");
        assertThat(chips.get(2).text(italian)).contains("Zucchina");
        assertThat(chips.get(3).text(italian)).isEqualTo("Cosa posso seminare nell’aiuola Nord?");
    }

    @Test
    void aGardenWithoutPlotsNeverSuggestsAPlotQuestion() {
        assertThat(keys(SuggestedQuestions.of(6, true, List.of(), null)))
                .containsExactly("ask.suggest.sow", "ask.suggest.companions.summer", "ask.suggest.sowing.summer");
    }

    @Test
    void everyChipSaysSomethingInBothLanguages() {
        for (int month = 1; month <= 12; month++) {
            for (SuggestedQuestions.Suggestion chip
                    : SuggestedQuestions.of(month, true, List.of("Pomodoro", "Fava"), "Nord")) {
                for (NoteText.Texts texts : List.of(italian, english)) {
                    assertThat(chip.text(texts)).isNotBlank().endsWith("?").doesNotContain("{0}");
                }
            }
        }
    }
}
