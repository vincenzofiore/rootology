package app.rootology.facts;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule `frost-suitability-v1` (#147): a perennial that stands less cold than the site's 1-in-5 winter minimum
 * does not survive there in open ground; in a pot it is sheltered in winter, in a greenhouse the rule is silent.
 */
class FrostSuitabilityRuleTest {

    private static final ClimateCells CELLS = new ClimateCells();

    /** Lemon stands -3 °C, kumquat -10 °C (crops-v1.csv, editorial). */
    private static final Crop LEMON = crop("lemon", "PERENNIAL", -3.0);
    private static final Crop KUMQUAT = crop("kumquat", "PERENNIAL", -10.0);

    private static Crop crop(String id, String lifecycle, Double hardiness) {
        return new Crop(id, id, id, id, "Rutaceae", null, null, "TENDER", "il", lifecycle, hardiness,
                hardiness == null ? null : "rootology-editorial", "FOOD");
    }

    /** A garden's profile: the nearest cell, corrected for the altitude. */
    private static SiteProfile garden(double latitude, double longitude, Integer altitude) {
        return CELLS.nearest(latitude, longitude)
                .map(cell -> FrostSeasonRule.profile(cell, altitude, null)).orElseThrow();
    }

    @Test
    void aLemonSurvivesTheWinterInVasto() {
        FrostSuitabilityRule.Result result = FrostSuitabilityRule.of(LEMON, garden(42.11, 14.71, 144), null)
                .orElseThrow();

        assertThat(result.verdict()).isEqualTo(FrostSuitabilityRule.Verdict.SURVIVES);
        assertThat(result.hardiness()).isEqualTo(-3.0);
        assertThat(result.winterMinimum()).as("the Vasto cell's -1.4 °C, corrected for 9 m").isEqualTo(-1.5);
    }

    @Test
    void aLemonDoesNotSurviveTheWinterInTurin() {
        // The Turin cell is 45.1/7.7 at -10.4 °C; the garden at 205 m is 38 m below it, so -10.2 °C.
        SiteProfile torino = garden(45.07, 7.69, 205);
        assertThat(torino.winterMinimum()).isEqualTo(-10.2);

        FrostSuitabilityRule.Result result = FrostSuitabilityRule.of(LEMON, torino, "GROUND").orElseThrow();

        assertThat(result.verdict()).isEqualTo(FrostSuitabilityRule.Verdict.NOT_IN_OPEN_GROUND);
        assertThat(result.hardiness()).isEqualTo(-3.0);
        assertThat(result.winterMinimum()).isEqualTo(-10.2);
    }

    @Test
    void aKumquatSurvivesTheWinterInMilan() {
        // Milan's reference cell is 45.5/9.2 at -6.8 °C, with no altitude correction.
        SiteProfile milano = ReferenceSite.NORD.profile(CELLS);
        assertThat(milano.winterMinimum()).isEqualTo(-6.8);

        assertThat(FrostSuitabilityRule.of(KUMQUAT, milano, null).orElseThrow().verdict())
                .isEqualTo(FrostSuitabilityRule.Verdict.SURVIVES);
    }

    @Test
    void inAPotTheVerdictIsToShelterIt() {
        SiteProfile torino = garden(45.07, 7.69, 205);

        assertThat(FrostSuitabilityRule.of(LEMON, torino, "POT").orElseThrow().verdict())
                .isEqualTo(FrostSuitabilityRule.Verdict.SHELTER_IN_POT);
        assertThat(FrostSuitabilityRule.of(LEMON, torino, "PLANTER").orElseThrow().verdict())
                .as("a planter is moved like a pot").isEqualTo(FrostSuitabilityRule.Verdict.SHELTER_IN_POT);
        assertThat(FrostSuitabilityRule.of(LEMON, torino, "RAISED_BED").orElseThrow().verdict())
                .as("a raised bed stays where it is").isEqualTo(FrostSuitabilityRule.Verdict.NOT_IN_OPEN_GROUND);
        assertThat(FrostSuitabilityRule.of(LEMON, torino, null).orElseThrow().verdict())
                .as("no area: the question is about open ground")
                .isEqualTo(FrostSuitabilityRule.Verdict.NOT_IN_OPEN_GROUND);
    }

    @Test
    void inAGreenhouseTheRuleSaysNothing() {
        assertThat(FrostSuitabilityRule.of(LEMON, garden(45.07, 7.69, 205), "GREENHOUSE")).isEmpty();
    }

    @Test
    void anAnnualOrAPerennialWithoutHardinessSaysNothing() {
        SiteProfile torino = garden(45.07, 7.69, 205);

        assertThat(FrostSuitabilityRule.of(crop("tomato", "ANNUAL", -1.0), torino, null)).isEmpty();
        assertThat(FrostSuitabilityRule.of(crop("feijoa", "PERENNIAL", null), torino, null)).isEmpty();
        assertThat(FrostSuitabilityRule.of(LEMON, null, null)).isEmpty();
    }

    @Test
    void theAltitudeCorrectionMovesABorderlineCase() {
        // A plant that stands -10.3 °C does not survive the cell's -10.4 °C, but does survive the -10.2 °C of a
        // garden 38 m below it.
        Crop borderline = crop("borderline", "PERENNIAL", -10.3);
        ClimateCells.Cell cell = CELLS.nearest(45.07, 7.69).orElseThrow();
        SiteProfile reference = FrostSeasonRule.reference(cell, "NORD");
        assertThat(reference.winterMinimum()).isEqualTo(-10.4);
        assertThat(FrostSuitabilityRule.of(borderline, reference, null).orElseThrow().verdict())
                .isEqualTo(FrostSuitabilityRule.Verdict.NOT_IN_OPEN_GROUND);

        Optional<FrostSuitabilityRule.Result> corrected =
                FrostSuitabilityRule.of(borderline, garden(45.07, 7.69, 205), null);
        assertThat(corrected.orElseThrow().winterMinimum()).isEqualTo(-10.2);
        assertThat(corrected.orElseThrow().verdict()).isEqualTo(FrostSuitabilityRule.Verdict.SURVIVES);
    }
}
