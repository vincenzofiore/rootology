package app.rootology.alerts;

import app.rootology.officialwarnings.OfficialWarning;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The one line on MeteoAlarm's official warnings in the morning alert (#168), pure and without a network. */
class OfficialWarningLineTest {

    /** A warning of Abruzzo, EMMA_ID IT013, with only the fields the line reads. */
    private static OfficialWarning warning(int level, int type) {
        return new OfficialWarning("w-" + level + "-" + type, "IT013", "Abruzzo", level, type, "event", "severity",
                null, null, null, null, null, null, null, null);
    }

    @Test
    void oneYellowRainIsOneActiveWarning() {
        List<OfficialWarning> warnings = List.of(warning(2, 10));

        assertThat(OfficialWarningLine.title(warnings)).isEqualTo("Allerte ufficiali in Abruzzo");
        assertThat(OfficialWarningLine.body(warnings)).isEqualTo("1 allerta attiva, la più alta gialla (pioggia). "
                + "Fonte: MeteoAlarm, Servizio Meteorologico dell’Aeronautica Militare. "
                + "Il dettaglio è nella pagina dell’orto.");
    }

    @Test
    void twoWarningsNameTheCountAndTheHighestLevelWithItsPhenomenon() {
        List<OfficialWarning> warnings = List.of(warning(3, 1), warning(2, 10));

        assertThat(OfficialWarningLine.body(warnings))
                .startsWith("2 allerte attive, la più alta arancione (vento).");
        assertThat(OfficialWarningLine.body(List.of(warning(2, 10), warning(3, 1))))
                .as("the highest level wins whatever the order")
                .startsWith("2 allerte attive, la più alta arancione (vento).");
    }

    @Test
    void anUnknownLevelIsSaidToBeSignalledAndNeverGuessed() {
        assertThat(OfficialWarningLine.body(List.of(warning(0, 10)))).contains("la più alta segnalata (pioggia)");
        assertThat(OfficialWarningLine.body(List.of(warning(1, 10))))
                .as("green is no warning: the level stays unnamed").contains("la più alta segnalata");
    }

    @Test
    void anUnknownPhenomenonIsAnotherOne() {
        assertThat(OfficialWarningLine.body(List.of(warning(4, 99))))
                .startsWith("1 allerta attiva, la più alta rossa (altro).");
    }
}
