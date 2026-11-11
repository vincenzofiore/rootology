package app.rootology.facts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Rule `frost-season-v1` (#62): the two safe months, the 15th threshold and the altitude correction. */
class FrostSeasonRuleTest {

    private static final ClimateCells CELLS = new ClimateCells();

    private static SiteProfile at(double latitude, double longitude, Integer altitude) {
        return CELLS.nearest(latitude, longitude)
                .map(cell -> FrostSeasonRule.profile(cell, altitude, null)).orElseThrow();
    }

    @Test
    void theSixSitesHaveTheSafeMonthsOfTheReviewedTable() {
        SiteProfile vasto = at(42.11, 14.71, 2);
        assertThat(vasto.lastFrostDay()).isEqualTo(47);
        assertThat(vasto.firstFrostDay()).isNull();
        assertThat(vasto.firstSafeMonth()).isEqualTo(3);
        assertThat(vasto.lastSafeMonth()).isEqualTo(12);

        SiteProfile torino = at(45.07, 7.69, 205);
        assertThat(torino.firstSafeMonth()).isEqualTo(4);
        assertThat(torino.lastSafeMonth()).isEqualTo(10);

        SiteProfile milano = at(45.46, 9.19, 108);
        assertThat(milano.firstSafeMonth()).isEqualTo(4);
        assertThat(milano.lastSafeMonth()).isEqualTo(11);

        SiteProfile palermo = at(38.12, 13.36, 13);
        assertThat(palermo.lastFrostDay()).isNull();
        assertThat(palermo.firstFrostDay()).isNull();
        assertThat(palermo.firstSafeMonth()).isEqualTo(1);
        assertThat(palermo.lastSafeMonth()).isEqualTo(12);

        SiteProfile aosta = at(45.74, 7.32, 585);
        assertThat(aosta.firstSafeMonth()).isEqualTo(5);
        assertThat(aosta.lastSafeMonth()).isEqualTo(10);

        SiteProfile lyon = at(45.76, 4.84, 171);
        assertThat(lyon.firstSafeMonth()).isEqualTo(4);
        assertThat(lyon.lastSafeMonth()).isEqualTo(11);
    }

    @Test
    void theFifteenthIsTheThreshold() {
        assertThat(FrostSeasonRule.firstSafeMonth(46)).as("15 February stays February").isEqualTo(2);
        assertThat(FrostSeasonRule.firstSafeMonth(47)).as("16 February moves to March").isEqualTo(3);
        assertThat(FrostSeasonRule.lastSafeMonth(319)).as("15 November moves to October").isEqualTo(10);
        assertThat(FrostSeasonRule.lastSafeMonth(320)).as("16 November stays November").isEqualTo(11);
        assertThat(FrostSeasonRule.lastSafeMonth(315)).as("11 November moves to October").isEqualTo(10);
        assertThat(FrostSeasonRule.firstSafeMonth(null)).isEqualTo(1);
        assertThat(FrostSeasonRule.lastSafeMonth(null)).isEqualTo(12);
    }

    @Test
    void theAltitudeCorrectionSaysWhatItDid() {
        assertThat(at(45.07, 7.69, 205).correction()).isEqualTo(SiteProfile.Correction.APPLIED);
        assertThat(at(42.11, 14.71, 2).correction()).as("a coast keeps the cell's dates")
                .isEqualTo(SiteProfile.Correction.COAST);
        assertThat(at(45.74, 7.32, 585).correction()).as("500 m cap").isEqualTo(SiteProfile.Correction.CAPPED);
        assertThat(at(45.07, 7.69, null).correction()).isEqualTo(SiteProfile.Correction.MISSING);
        assertThat(at(45.07, 7.69, -9999).correction()).as("the sentinel is no altitude")
                .isEqualTo(SiteProfile.Correction.MISSING);
        assertThat(at(45.07, 7.69, 205).sources()).contains(FrostSeasonRule.GTOPO30);
        assertThat(at(42.11, 14.71, 2).sources()).as("a coast did not use the altitude")
                .doesNotContain(FrostSeasonRule.GTOPO30);
    }

    @Test
    void aReferenceSiteKeepsTheCellValues() {
        SiteProfile milano = ReferenceSite.NORD.profile(CELLS);
        assertThat(milano.zone()).isEqualTo("NORD");
        assertThat(milano.correction()).isEqualTo(SiteProfile.Correction.NONE);
        assertThat(milano.sources()).doesNotContain(FrostSeasonRule.GTOPO30);
        assertThat(milano.firstSafeMonth()).isEqualTo(4);
        assertThat(milano.lastSafeMonth()).isEqualTo(11);
    }

    @Test
    void theNearestCellIsLookedForAroundThePosition() {
        // Torino is at 45.07/7.69: the nearest land cell is 45.1/7.7, not the rounded 45.1/7.7 only by luck.
        assertThat(CELLS.nearest(45.07, 7.69)).hasValueSatisfying(cell -> {
            assertThat(cell.latitude()).isEqualTo(45.1);
            assertThat(cell.longitude()).isEqualTo(7.7);
        });
        assertThat(CELLS.nearest(0.0, 0.0)).as("no cell at sea, far from land").isEmpty();
    }
}
