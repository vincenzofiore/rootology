package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.pipeline.ContextSnapshot;
import io.github.vincenzofiore.rootology.pipeline.ContextSnapshot.MonthFrom;
import io.github.vincenzofiore.rootology.pipeline.ContextSnapshot.ZoneFrom;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** The context line of an answer (#55), in the page's language. */
class ContextTextTest {

    private final Translations translations = new Translations();
    private final NoteText.Texts italian = (key, args) -> translations.getTranslation(key, Locale.ITALIAN, args);
    private final NoteText.Texts english = (key, args) -> translations.getTranslation(key, Locale.ENGLISH, args);

    @Test
    void aQuestionAskedInsideAGardenSaysTheGardenAndWhereTheZoneCameFrom() {
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", "NORD", ZoneFrom.GARDEN,
                "NOVEMBER", MonthFrom.DEFAULT, null);

        assertThat(ContextText.line(context, italian, Locale.ITALIAN))
                .isEqualTo("Risposta per: Casa · Orto sul tetto · zona NORD (dall’orto) · novembre");
        assertThat(ContextText.line(context, english, Locale.ENGLISH))
                .isEqualTo("Answered for: Casa · Orto sul tetto · zone NORD (from the garden) · November");
    }

    @Test
    void aQuestionAboutAPlotNamesThePlotAfterTheGarden() {
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", "NORD", ZoneFrom.GARDEN,
                "NOVEMBER", MonthFrom.DEFAULT, "Aiuola B");

        assertThat(ContextText.line(context, italian, Locale.ITALIAN))
                .isEqualTo("Risposta per: Casa · Orto sul tetto · Aiuola B · zona NORD (dall’orto) · novembre");
    }

    @Test
    void theSpaceLevelSaysNoGardenAndTheDefaultZone() {
        ContextSnapshot context = new ContextSnapshot("Casa", null, "CENTRO", ZoneFrom.DEFAULT,
                "NOVEMBER", MonthFrom.DEFAULT, null);

        assertThat(ContextText.line(context, italian, Locale.ITALIAN))
                .isEqualTo("Risposta per: Casa · senza orto · zona CENTRO (predefinita) · novembre");
    }

    @Test
    void aZoneAndAMonthNamedInTheQuestionSaySoAndAMonthlessKindHasNone() {
        ContextSnapshot asked = new ContextSnapshot("Casa", "Orto", "SUD", ZoneFrom.QUESTION,
                "MARCH", MonthFrom.QUESTION, null);
        ContextSnapshot companions = new ContextSnapshot("Casa", "Orto", null, null, null, null, null);

        assertThat(ContextText.line(asked, italian, Locale.ITALIAN))
                .isEqualTo("Risposta per: Casa · Orto · zona SUD (dalla domanda) · marzo (dalla domanda)");
        assertThat(ContextText.line(companions, italian, Locale.ITALIAN)).isEqualTo("Risposta per: Casa · Orto");
    }
}
