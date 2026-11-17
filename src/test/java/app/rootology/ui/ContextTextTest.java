package app.rootology.ui;

import app.rootology.pipeline.ContextSnapshot;
import app.rootology.pipeline.ContextSnapshot.ClimateFrom;
import app.rootology.pipeline.ContextSnapshot.MonthFrom;
import app.rootology.pipeline.ContextSnapshot.ZoneFrom;
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
    void aCalendarQuestionShowsTheLastFrostInsteadOfTheZone() {
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", null, null,
                "NOVEMBER", MonthFrom.DEFAULT, null, 90, 320, ClimateFrom.GARDEN);

        assertThat(ContextText.line(context, italian, Locale.ITALIAN))
                .isEqualTo("Risposta per: Casa · Orto sul tetto · ultime gelate fine marzo (dall’orto) · novembre");
        assertThat(ContextText.line(context, english, Locale.ENGLISH))
                .isEqualTo("Answered for: Casa · Orto sul tetto · last frost late March (from the garden) · November");

        ContextSnapshot early = new ContextSnapshot("Casa", "Orto", null, null, "NOVEMBER", MonthFrom.DEFAULT, null,
                96, 320, ClimateFrom.QUESTION);
        assertThat(ContextText.line(early, italian, Locale.ITALIAN))
                .isEqualTo("Risposta per: Casa · Orto · ultime gelate inizio aprile (dalla domanda) · novembre");

        ContextSnapshot none = new ContextSnapshot("Casa", "Orto", null, null, "NOVEMBER", MonthFrom.DEFAULT, null,
                null, null, ClimateFrom.DEFAULT);
        assertThat(ContextText.line(none, italian, Locale.ITALIAN))
                .isEqualTo("Risposta per: Casa · Orto · nessuna gelata in primavera · novembre");
    }

    @Test
    void aPlaceNamedInTheQuestionIsShownInsteadOfTheGarden() {
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", null, null,
                "NOVEMBER", MonthFrom.DEFAULT, null, 90, 320, ClimateFrom.PLACE, "Bergamo");

        assertThat(ContextText.line(context, italian, Locale.ITALIAN))
                .isEqualTo("Risposta per: Casa · Bergamo · ultime gelate fine marzo (dal luogo) · novembre");
        assertThat(ContextText.line(context, english, Locale.ENGLISH))
                .isEqualTo("Answered for: Casa · Bergamo · last frost late March (from the place) · November");
        assertThat(ContextText.brief(context, italian, Locale.ITALIAN)).isEqualTo("Bergamo · novembre");
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

    @Test
    void theFoldOfAnAnswerKeepsOnlyWhatTheBriefLineDoesNotSay() {
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", null, null, "NOVEMBER",
                MonthFrom.DEFAULT, "Aiuola B", 90, 320, ClimateFrom.GARDEN);

        assertThat(ContextText.detail(context, italian, Locale.ITALIAN))
                .as("the garden and the area are in the line above")
                .isEqualTo("ultime gelate fine marzo (dall’orto)");
        assertThat(ContextText.detail(context, english, Locale.ENGLISH))
                .isEqualTo("last frost late March (from the garden)");

        ContextSnapshot question = new ContextSnapshot("Casa", "Orto", null, null, "MARCH", MonthFrom.QUESTION,
                null, 96, 320, ClimateFrom.QUESTION);
        assertThat(ContextText.detail(question, italian, Locale.ITALIAN))
                .isEqualTo("ultime gelate inizio aprile (dalla domanda) · marzo (dalla domanda)");
    }

    @Test
    void theFoldOfAnAnsweredQuestionSaysNothingWhenTheBriefLineSaysItAll() {
        ContextSnapshot plain = new ContextSnapshot("Casa", "Orto", null, null, "NOVEMBER", MonthFrom.DEFAULT, null);
        ContextSnapshot companion = new ContextSnapshot("Casa", "Orto", null, null, null, null, null);

        assertThat(ContextText.detail(plain, italian, Locale.ITALIAN)).isEmpty();
        assertThat(ContextText.detail(companion, italian, Locale.ITALIAN)).isEmpty();
    }

    @Test
    void theBriefLineAboveAnAnswerSaysOnlyTheGardenTheAreaAndTheMonth() {
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", "NORD", ZoneFrom.GARDEN,
                "NOVEMBER", MonthFrom.QUESTION, "Aiuola B");
        ContextSnapshot noGarden = new ContextSnapshot("Casa", null, null, null, "MARCH", MonthFrom.DEFAULT, null);

        assertThat(ContextText.brief(context, italian, Locale.ITALIAN)).isEqualTo("Orto sul tetto · Aiuola B · novembre");
        assertThat(ContextText.brief(context, english, Locale.ENGLISH)).isEqualTo("Orto sul tetto · Aiuola B · November");
        assertThat(ContextText.brief(noGarden, italian, Locale.ITALIAN)).isEqualTo("Casa · senza orto · marzo");
    }

    @Test
    void theBriefLineLeavesOutTheGardenOfThePageItIsShownIn() {
        // #121: inside a garden the breadcrumb and the page's context line already say which garden it is.
        ContextSnapshot context = new ContextSnapshot("Casa", "Orto sul tetto", null, null, "NOVEMBER",
                MonthFrom.DEFAULT, "Aiuola B");

        assertThat(ContextText.brief(context, "Orto sul tetto", italian, Locale.ITALIAN))
                .isEqualTo("Aiuola B · novembre");
        assertThat(ContextText.brief(context, "Altro orto", italian, Locale.ITALIAN))
                .as("a garden that differs from the page's is named")
                .isEqualTo("Orto sul tetto · Aiuola B · novembre");
        assertThat(ContextText.brief(context, null, italian, Locale.ITALIAN))
                .as("the space level names the garden")
                .isEqualTo("Orto sul tetto · Aiuola B · novembre");

        ContextSnapshot nothingElse = new ContextSnapshot("Casa", "Orto sul tetto", null, null, null, null, null);
        assertThat(ContextText.brief(nothingElse, "Orto sul tetto", italian, Locale.ITALIAN))
                .as("the line goes when the garden was all it said")
                .isEmpty();
    }

    @Test
    void theFoldLeavesOutTheClimateThePagesOwnLineAlreadyShows() {
        // #121: the page's context line says "ultime gelate metà febbraio", so the fold says nothing new.
        ContextSnapshot garden = new ContextSnapshot("Casa", "Orto sul tetto", null, null, "NOVEMBER",
                MonthFrom.DEFAULT, null, 90, 320, ClimateFrom.GARDEN);

        assertThat(ContextText.detail(garden, "Orto sul tetto", italian, Locale.ITALIAN)).isEmpty();
        assertThat(ContextText.detail(garden, "Altro orto", italian, Locale.ITALIAN))
                .isEqualTo("ultime gelate fine marzo (dall’orto)");
        assertThat(ContextText.detail(garden, null, italian, Locale.ITALIAN))
                .isEqualTo("ultime gelate fine marzo (dall’orto)");

        ContextSnapshot place = new ContextSnapshot("Casa", "Orto sul tetto", null, null, "NOVEMBER",
                MonthFrom.DEFAULT, null, 90, 320, ClimateFrom.PLACE, "Bergamo");
        assertThat(ContextText.detail(place, "Orto sul tetto", italian, Locale.ITALIAN))
                .as("a place the question named is not in the page's line")
                .isEqualTo("ultime gelate fine marzo (dal luogo)");

        ContextSnapshot zone = new ContextSnapshot("Casa", "Orto sul tetto", "NORD", ZoneFrom.GARDEN,
                "NOVEMBER", MonthFrom.DEFAULT, null);
        assertThat(ContextText.detail(zone, "Orto sul tetto", italian, Locale.ITALIAN))
                .as("a zone from a row stored before #62 stays")
                .isEqualTo("zona NORD (dall’orto)");

        ContextSnapshot asked = new ContextSnapshot("Casa", "Orto sul tetto", null, null, "MARCH",
                MonthFrom.QUESTION, null, 90, 320, ClimateFrom.GARDEN);
        assertThat(ContextText.detail(asked, "Orto sul tetto", italian, Locale.ITALIAN))
                .as("a month the question named stays")
                .isEqualTo("marzo (dalla domanda)");
    }
}
