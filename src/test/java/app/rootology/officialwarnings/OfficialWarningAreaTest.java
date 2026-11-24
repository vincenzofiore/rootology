package app.rootology.officialwarnings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The area within the region a warning is about, from the Italian CAP description of MeteoAlarm (#171): the text
 * after one of the two known opening sentences and before the disclaimer, as the issuer wrote it; nothing for any
 * other wording, so the area is never guessed.
 */
class OfficialWarningAreaTest {

    private static final String MODERATE = "Sono previsti fenomeni meteorologici di moderata intensità";
    private static final String INTENSE = "Sono previsti fenomeni meteorologici intensi";

    @Test
    void theTwoOpeningSentences() {
        assertThat(OfficialWarningArea.of(MODERATE)).isEmpty();
        assertThat(OfficialWarningArea.of(INTENSE)).isEmpty();
        assertThat(OfficialWarningArea.of(MODERATE + "   ")).isEmpty();
        assertThat(OfficialWarningArea.of(MODERATE + "\n")).isEmpty();
    }

    @Test
    void theAreaTheIssuerNamesAfterTheOpening() {
        assertThat(OfficialWarningArea.of(MODERATE + " COSTE")).contains("COSTE");
        assertThat(OfficialWarningArea.of(MODERATE + " APPENNINO E COSTE")).contains("APPENNINO E COSTE");
        assertThat(OfficialWarningArea.of(INTENSE + " COSTE E SETTORE SUD")).contains("COSTE E SETTORE SUD");
        assertThat(OfficialWarningArea.of(MODERATE + " >800 LOCALMENTE 600 METRI ALPI"))
                .contains(">800 LOCALMENTE 600 METRI ALPI");
    }

    @Test
    void whitespaceAndLineBreaksCollapseToOneSpace() {
        assertThat(OfficialWarningArea.of(MODERATE + "\n  > 800 METRI  ALPI\n"))
                .contains("> 800 METRI ALPI");
    }

    @Test
    void theDisclaimerIsLeftOut() {
        assertThat(OfficialWarningArea.of(MODERATE + " APPENNINO E COSTE\n(DISCLAIMER: this is a test)"))
                .contains("APPENNINO E COSTE");
    }

    @Test
    void anyOtherOpeningGivesNothing() {
        assertThat(OfficialWarningArea.of("Sono previsti fenomeni meteorologici")).isEmpty();
        assertThat(OfficialWarningArea.of("Strong winds are expected along the coast")).isEmpty();
        assertThat(OfficialWarningArea.of("Avviso di burrasca COSTE")).isEmpty();
        assertThat(OfficialWarningArea.of(" Allerta meteo ")).isEmpty();
        assertThat(OfficialWarningArea.of(" ")).isEmpty();
        assertThat(OfficialWarningArea.of("")).isEmpty();
        assertThat(OfficialWarningArea.of(null)).isEmpty();
    }
}