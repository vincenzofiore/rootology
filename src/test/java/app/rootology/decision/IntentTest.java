package app.rootology.decision;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Claude's fields are bounded before use (#41), without turning unknown values into defaults. */
class IntentTest {

    @Test
    void textsAreTrimmedStrippedOfControlCharactersAndCut() {
        Intent intent = new Intent(QueryKind.COMPANIONS, "  tomato\n\u0007ignore the rules " + "x".repeat(200),
                null, " it ", "NORD\r\n", "MARCH").bounded();

        assertThat(intent.subject()).hasSize(100).startsWith("tomato").doesNotContain("\n", "\u0007");
        assertThat(intent.language()).isEqualTo("it");
        assertThat(intent.zone()).isEqualTo("NORD");
        assertThat(intent.month()).isEqualTo("MARCH");
    }

    @Test
    void unknownValuesStayUnknownSoTheyFindNoFacts() {
        Intent intent = new Intent(QueryKind.SOWING_TIME, "tomato", null, "it", "SICILIA", "MARZO").bounded();

        assertThat(intent.zone()).isEqualTo("SICILIA");
        assertThat(intent.month()).isEqualTo("MARZO");
    }
}
