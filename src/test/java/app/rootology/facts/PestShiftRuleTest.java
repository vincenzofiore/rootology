package app.rootology.facts;

import app.rootology.facts.CuratedKnowledge.WindowRow;
import app.rootology.facts.FrostShiftRule.InferredWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule `pest-shift-v1` (#162): the months of a pest or disease on a crop move as that crop's own calendar
 * moves at the site, and stay curated where the crop's calendar does not move.
 */
class PestShiftRuleTest {

    private static final ClimateCells CELLS = new ClimateCells();
    private static final CropCatalog CROPS = new CropCatalog();
    private static final List<WindowRow> CURATED = CuratedKnowledgeReader.read(CROPS).windows();
    private static final Map<String, String> GROUPS = CROPS.all().stream()
            .collect(Collectors.toMap(Crop::id, Crop::frostGroup));

    private static List<InferredWindow> at(double latitude, double longitude, Integer altitude) {
        SiteProfile profile = CELLS.nearest(latitude, longitude)
                .map(cell -> FrostSeasonRule.profile(cell, altitude, null)).orElseThrow();
        return FrostShiftRule.infer(CURATED, GROUPS, profile);
    }

    private static PestShiftRule.Shifted shiftAt(double latitude, double longitude, Integer altitude, String crop,
                                                 int from, int to) {
        return PestShiftRule.shift(crop, from, to, CURATED, at(latitude, longitude, altitude));
    }

    @Test
    void aTenderCropsPestsComeLaterInAosta() {
        // The tomato's own calendar opens a month or two later at Aosta, and late blight follows it.
        PestShiftRule.Shifted blight = shiftAt(45.74, 7.32, 585, "tomato", 5, 9);

        assertThat(blight.fromMonth()).isEqualTo(6);
        assertThat(blight.toMonth()).isEqualTo(10);
        assertThat(blight.derivedBy()).isEqualTo(PestShiftRule.ID);
        assertThat(blight.moved()).isTrue();
    }

    @Test
    void aLongCropsPestsFollowItsOwnCalendarInTorino() {
        // The lettuce's sowing starts a month later and its harvest ends a month earlier at Torino.
        PestShiftRule.Shifted downyMildew = shiftAt(45.07, 7.69, 205, "lettuce", 4, 6);

        assertThat(downyMildew.fromMonth()).isEqualTo(5);
        assertThat(downyMildew.toMonth()).isEqualTo(5);
        assertThat(downyMildew.derivedBy()).isEqualTo(PestShiftRule.ID);
    }

    @Test
    void vastoAndPalermoKeepTheCuratedMonths() {
        for (double[] site : new double[][]{{42.11, 14.71, 2}, {38.12, 13.36, 13}}) {
            PestShiftRule.Shifted blight = shiftAt(site[0], site[1], (int) site[2], "tomato", 5, 9);

            assertThat(blight.fromMonth()).as("from at " + site[0] + "/" + site[1]).isEqualTo(5);
            assertThat(blight.toMonth()).as("to at " + site[0] + "/" + site[1]).isEqualTo(9);
            assertThat(blight.derivedBy()).isNull();
        }
    }

    @Test
    void aRowThatWrapsOverTheNewYearStaysAsItIs() {
        // The lettuce's calendar moves at Torino, but a row crossing the new year has no month to move.
        PestShiftRule.Shifted row = shiftAt(45.07, 7.69, 205, "lettuce", 11, 2);

        assertThat(row.fromMonth()).isEqualTo(11);
        assertThat(row.toMonth()).isEqualTo(2);
        assertThat(row.derivedBy()).isNull();
    }

    @Test
    void aYearRoundRowStaysAsItIs() {
        PestShiftRule.Shifted row = shiftAt(45.07, 7.69, 205, "lettuce", 1, 12);

        assertThat(row.fromMonth()).isEqualTo(1);
        assertThat(row.toMonth()).isEqualTo(12);
        assertThat(row.derivedBy()).isNull();
    }

    @Test
    void aMovedRowIsNeverEmpty() {
        // The lettuce's sowing starts a month later and its harvest ends a month earlier at Torino: 9-10
        // would become 10-9, so it becomes October alone.
        PestShiftRule.Shifted row = shiftAt(45.07, 7.69, 205, "lettuce", 9, 10);

        assertThat(row.fromMonth()).isEqualTo(10);
        assertThat(row.toMonth()).isEqualTo(10);
        assertThat(row.derivedBy()).isEqualTo(PestShiftRule.ID);
    }

    @Test
    void aCropWithNoDerivedWindowKeepsItsMonths() {
        // A window the site does not derive leaves the row curated: the crop's only window is a winter harvest,
        // which this site does not derive at all.
        WindowRow winterHarvest = new WindowRow("tomato", "CENTRO", "HARVEST", 11, 12, "", "rootology-editorial", false);
        SiteProfile cold = new SiteProfile(null, 45.1, 7.7, 243, 205, 0.98, 96, 313, 4, 10, null,
                SiteProfile.Correction.APPLIED, List.of("agera5"));

        PestShiftRule.Shifted row = PestShiftRule.shift("tomato", 5, 9, List.of(winterHarvest),
                FrostShiftRule.infer(List.of(winterHarvest), GROUPS, cold));

        assertThat(row.fromMonth()).isEqualTo(5);
        assertThat(row.toMonth()).isEqualTo(9);
        assertThat(row.derivedBy()).isNull();
    }
}
