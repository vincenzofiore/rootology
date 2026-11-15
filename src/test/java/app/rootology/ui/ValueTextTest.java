package app.rootology.ui;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** Answer values in the person's language (#110): months, crops and organisms, and nothing guessed. */
class ValueTextTest {

    @Test
    void aMonthIsCalledAsTheLanguageCallsIt() {
        assertThat(ValueText.of("AUGUST", Locale.ITALIAN)).isEqualTo("agosto");
        assertThat(ValueText.of("AUGUST", Locale.ENGLISH)).isEqualTo("August");
        assertThat(ValueText.of("FEBRUARY", Locale.ITALIAN)).isEqualTo("febbraio");
        assertThat(ValueText.of("FEBRUARY", Locale.ENGLISH)).isEqualTo("February");
    }

    @Test
    void aCropIsNamedAsTheCatalogueNamesIt() {
        assertThat(ValueText.of("Basil", Locale.ITALIAN)).isEqualTo("Basilico");
        assertThat(ValueText.of("Basilico", Locale.ENGLISH)).isEqualTo("Basil");
        // The stable id is found too, for a value that carries one.
        assertThat(ValueText.of("tomato", Locale.ITALIAN)).isEqualTo("Pomodoro");
    }

    @Test
    void aPestIsNamedAsTheCatalogueNamesIt() {
        assertThat(ValueText.of("Dorifora", Locale.ENGLISH)).isEqualTo("Colorado potato beetle");
        assertThat(ValueText.of("Colorado potato beetle", Locale.ITALIAN)).isEqualTo("Dorifora");
    }

    @Test
    void whatTheKnowledgeBaseDoesNotKnowStaysExactlyAsWritten() {
        assertThat(ValueText.of("Aiuola B", Locale.ENGLISH)).isEqualTo("Aiuola B");
        assertThat(ValueText.of("lavanda", Locale.ITALIAN)).isEqualTo("lavanda");
        assertThat(ValueText.of("SUMMER", Locale.ITALIAN)).isEqualTo("SUMMER");
        assertThat(ValueText.of("", Locale.ITALIAN)).isEmpty();
        assertThat(ValueText.of(null, Locale.ITALIAN)).isNull();
    }

    @Test
    void aSentenceIsComparedIgnoringCaseAndAccents() {
        assertThat(ValueText.says("Puoi seminare da agosto a ottobre.", "agosto")).isTrue();
        assertThat(ValueText.says("Ecco perché il pomodoro.", "perche")).isTrue();
        assertThat(ValueText.says("Puoi seminare da agosto.", "settembre")).isFalse();
        assertThat(ValueText.says("Il rapanello cresce in fretta.", "rapa")).as("a whole word only").isFalse();
        assertThat(ValueText.says("Aglio, cipolla.", "aglio")).isTrue();
    }
}
