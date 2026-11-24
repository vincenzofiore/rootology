package app.rootology.officialwarnings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The fixed table from GeoNames' Italian region codes to MeteoAlarm's EMMA_ID (#136). */
class WarningZonesTest {

    @Test
    void everyItalianRegionHasItsMeteoAlarmZone() {
        assertThat(WarningZones.emmaId("IT", "01")).contains("IT013");
        assertThat(WarningZones.emmaId("IT", "02")).contains("IT017");
        assertThat(WarningZones.emmaId("IT", "03")).contains("IT001");
        assertThat(WarningZones.emmaId("IT", "04")).contains("IT016");
        assertThat(WarningZones.emmaId("IT", "05")).contains("IT008");
        assertThat(WarningZones.emmaId("IT", "06")).contains("IT020");
        assertThat(WarningZones.emmaId("IT", "07")).contains("IT012");
        assertThat(WarningZones.emmaId("IT", "08")).contains("IT007");
        assertThat(WarningZones.emmaId("IT", "09")).contains("IT003");
        assertThat(WarningZones.emmaId("IT", "10")).contains("IT011");
        assertThat(WarningZones.emmaId("IT", "11")).contains("IT014");
        assertThat(WarningZones.emmaId("IT", "12")).contains("IT005");
        assertThat(WarningZones.emmaId("IT", "13")).contains("IT015");
        assertThat(WarningZones.emmaId("IT", "14")).contains("IT019");
        assertThat(WarningZones.emmaId("IT", "15")).contains("IT018");
        assertThat(WarningZones.emmaId("IT", "16")).contains("IT009");
        assertThat(WarningZones.emmaId("IT", "17")).contains("IT002");
        assertThat(WarningZones.emmaId("IT", "18")).contains("IT010");
        assertThat(WarningZones.emmaId("IT", "19")).contains("IT004");
        assertThat(WarningZones.emmaId("IT", "20")).contains("IT006");
    }

    @Test
    void aGardenWithoutACountryIsInItaly() {
        assertThat(WarningZones.emmaId(null, "13")).contains("IT015");
    }

    @Test
    void aForeignCountryHasNoItalianZone() {
        assertThat(WarningZones.emmaId("FR", "13")).isEmpty();
        assertThat(WarningZones.emmaId("DE", "09")).isEmpty();
    }

    @Test
    void anUnknownOrMissingCodeHasNoZone() {
        assertThat(WarningZones.emmaId("IT", "99")).isEmpty();
        assertThat(WarningZones.emmaId("IT", "XX")).isEmpty();
        assertThat(WarningZones.emmaId("IT", null)).isEmpty();
        assertThat(WarningZones.emmaId(null, null)).isEmpty();
    }
}
