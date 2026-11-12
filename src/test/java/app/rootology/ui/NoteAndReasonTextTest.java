package app.rootology.ui;

import app.rootology.decision.Note;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** Notes and decision reasons in the person's language (#31). */
class NoteAndReasonTextTest {

    private final Translations translations = new Translations();
    private final NoteText.Texts italian = (key, args) -> translations.getTranslation(key, Locale.ITALIAN, args);
    private final NoteText.Texts english = (key, args) -> translations.getTranslation(key, Locale.ENGLISH, args);

    @Test
    void notesAreWordedWithTheirValuesAndDatesInTheLanguage() {
        Note minimum = Note.of(Note.Kind.FORECAST_MINIMUM, "-2.0", "2026-11-06", "");
        Note stale = Note.of(Note.Kind.NO_SMITH_PERIOD, "2026-11-04T07:30");
        Note excluded = Note.of(Note.Kind.FROST_EXCLUDED, "frost-risk-v1", "Pisello");

        assertThat(NoteText.text(minimum, italian, Locale.ITALIAN))
                .isEqualTo("Minima prevista nei prossimi 7 giorni: -2.0 °C, venerdì 6 novembre (MET Norway).");
        assertThat(NoteText.text(minimum, english, Locale.ENGLISH)).contains("Friday 6 November");
        assertThat(NoteText.text(stale, italian, Locale.ITALIAN)).endsWith("Previsione del 4 novembre 07:30: il servizio non risponde.");
        assertThat(NoteText.text(excluded, italian, Locale.ITALIAN))
                .isEqualTo("Sconsigliate adesso per rischio gelo: Pisello (regola frost-risk-v1).");
        assertThat(NoteText.tone(excluded)).isEqualTo(NoteText.Tone.RISK);
        assertThat(minimum.english()).isEqualTo("Forecast minimum in the next 7 days: -2.0 °C on Fri 6 Nov (MET Norway).");
    }

    @Test
    void theNamesOfAGardenOrPlotNotFoundAreFilledIn() {
        Note plot = Note.of(Note.Kind.PLOT_NOT_FOUND, "Nord", "Terrazzo");
        Note garden = Note.of(Note.Kind.GARDEN_OF_QUESTION, "Orto sul tetto", "Terrazzo");

        assertThat(NoteText.text(plot, italian, Locale.ITALIAN))
                .startsWith("Nell’orto Terrazzo non c’è un’aiuola che si chiami “Nord”");
        assertThat(NoteText.text(garden, english, Locale.ENGLISH)).doesNotContain("{0}", "{1}")
                .contains("Orto sul tetto", "Terrazzo");
    }

    @Test
    void everyNoteKindHasATextInBothLanguages() {
        for (Note.Kind kind : Note.Kind.values()) {
            Note note = switch (kind) {
                case FORECAST_MINIMUM -> Note.of(kind, "1.0", "2026-11-06", "");
                case SMITH_PERIOD -> Note.of(kind, "2026-11-06", "Pomodoro", "smith-period-v1", "");
                case NO_SMITH_PERIOD -> Note.of(kind, "");
                default -> Note.of(kind, "frost-risk-v1", "Pisello");
            };
            assertThat(NoteText.text(note, italian, Locale.ITALIAN)).as(kind.name()).doesNotStartWith("!");
            assertThat(NoteText.text(note, english, Locale.ENGLISH)).as(kind.name()).doesNotStartWith("!");
        }
    }

    @Test
    void knownReasonsAreTranslatedAndOthersKept() {
        assertThat(ReasonText.text("lavanda is not in the dataset", italian))
                .isEqualTo("lavanda non è nella base di conoscenza");
        assertThat(ReasonText.text("Jev: low confidence (0,42)", italian))
                .isEqualTo("Jev non è abbastanza sicuro (confidenza 0,42)");
        assertThat(ReasonText.text("Jev: the facts do not answer this (p=0.18)", english))
                .isEqualTo("the facts do not answer this question (probability 0.18)");
        assertThat(ReasonText.text("From experience", italian)).isEqualTo("From experience");
    }
}
