package app.rootology.facts;

import app.rootology.facts.CuratedKnowledge.WindowRow;
import app.rootology.facts.FrostShiftRule.InferredWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule `frost-shift-v1` (#62), checked against the 12-site table the owner reviewed. The expected values
 * are written from that table (the scratch script's output), not computed by the rule: Vasto, Torino,
 * Milano, Palermo, Aosta and Lyon.
 */
class FrostShiftRuleTest {

    private static final ClimateCells CELLS = new ClimateCells();
    private static final CropCatalog CROPS = new CropCatalog();
    private static final List<WindowRow> CURATED = CuratedKnowledgeReader.read(CROPS).windows();
    private static final List<WindowRow> CENTRO = CURATED.stream().filter(w -> w.zone().equals("CENTRO")).toList();
    private static final Map<String, String> GROUPS = CROPS.all().stream()
            .collect(Collectors.toMap(Crop::id, Crop::frostGroup));

    private static List<InferredWindow> at(double latitude, double longitude, Integer altitude) {
        SiteProfile profile = CELLS.nearest(latitude, longitude)
                .map(cell -> FrostSeasonRule.profile(cell, altitude, null)).orElseThrow();
        return FrostShiftRule.infer(CURATED, GROUPS, profile);
    }

    private static String tomatoTransplant(List<InferredWindow> inferred) {
        return inferred.stream().filter(i -> i.from().cropId().equals("tomato")
                        && i.from().activity().equals("TRANSPLANT"))
                .map(i -> i.window().fromMonth() + "-" + i.window().toMonth()).findFirst().orElseThrow();
    }

    private static String window(List<InferredWindow> inferred, String crop, String activity, int from) {
        return inferred.stream().filter(i -> i.from().cropId().equals(crop)
                        && i.from().activity().equals(activity) && i.from().fromMonth() == from)
                .map(i -> i.window().fromMonth() + "-" + i.window().toMonth()).findFirst().orElse("not derived");
    }

    private static void counts(List<InferredWindow> inferred, int same, int changed, int none) {
        int equal = 0;
        int different = 0;
        int missing = 0;
        for (WindowRow row : CENTRO) {
            InferredWindow match = inferred.stream().filter(i -> i.from().equals(row)).findFirst().orElse(null);
            if (match == null) {
                missing++;
            } else if (match.window().fromMonth() == row.fromMonth() && match.window().toMonth() == row.toMonth()) {
                equal++;
            } else {
                different++;
            }
        }
        assertThat(new int[]{equal, different, missing}).containsExactly(same, changed, none);
    }

    @Test
    void vastoKeepsEveryWindowAsItIs() {
        List<InferredWindow> inferred = at(42.11, 14.71, 2);

        counts(inferred, 92, 0, 0);
        assertThat(tomatoTransplant(inferred)).isEqualTo("4-5");
        assertThat(window(inferred, "garlic", "PLANT", 10)).isEqualTo("10-12");
        assertThat(window(inferred, "cauliflower", "HARVEST", 10)).isEqualTo("10-2");
    }

    @Test
    void torinoMovesOnlyWhatItsFrostDatesMakeImpossible() {
        List<InferredWindow> inferred = at(45.07, 7.69, 205);

        counts(inferred, 48, 37, 7);
        assertThat(tomatoTransplant(inferred)).isEqualTo("4-5");
        assertThat(window(inferred, "basil", "SOW", 3)).isEqualTo("4-6");
        assertThat(window(inferred, "basil", "HARVEST", 6)).isEqualTo("7-10");
        assertThat(window(inferred, "cauliflower", "SOW_SEEDBED", 5)).isEqualTo("4-6");
        assertThat(window(inferred, "cauliflower", "TRANSPLANT", 7)).isEqualTo("6-7");
        assertThat(window(inferred, "broccoli", "SOW_SEEDBED", 6)).isEqualTo("5-6");
        assertThat(window(inferred, "broccoli", "TRANSPLANT", 8)).isEqualTo("7-7");
        assertThat(window(inferred, "garlic", "PLANT", 10)).isEqualTo("9-11");
        assertThat(window(inferred, "lettuce", "SOW", 2)).isEqualTo("3-9");
        assertThat(window(inferred, "lettuce", "HARVEST", 3)).isEqualTo("4-10");
        assertThat(window(inferred, "carrot", "SOW", 2)).isEqualTo("3-7");
        assertThat(window(inferred, "carrot", "HARVEST", 5)).isEqualTo("6-10");
        // A winter harvest whose chain moved is not derived, ever.
        assertThat(window(inferred, "cauliflower", "HARVEST", 10)).isEqualTo("not derived");
        assertThat(window(inferred, "broccoli", "HARVEST", 10)).isEqualTo("not derived");
    }

    @Test
    void milanoMovesTheSpringSowingsAndTheLateHarvests() {
        List<InferredWindow> inferred = at(45.46, 9.19, 108);

        counts(inferred, 77, 15, 0);
        assertThat(tomatoTransplant(inferred)).isEqualTo("4-5");
        assertThat(window(inferred, "basil", "SOW", 3)).isEqualTo("4-6");
        assertThat(window(inferred, "cabbage", "HARVEST", 6)).isEqualTo("6-11");
        assertThat(window(inferred, "lettuce", "SOW", 2)).isEqualTo("3-9");
        assertThat(window(inferred, "carrot", "SOW", 2)).isEqualTo("3-7");
        assertThat(window(inferred, "chard", "HARVEST", 5)).isEqualTo("5-11");
    }

    @Test
    void palermoKeepsEveryWindowAsVasto() {
        List<InferredWindow> inferred = at(38.12, 13.36, 13);

        counts(inferred, 92, 0, 0);
        assertThat(tomatoTransplant(inferred)).isEqualTo("4-5");
    }

    @Test
    void aostaMovesTheTenderCropsTwoMonthsAndLosesTheWinterHarvests() {
        List<InferredWindow> inferred = at(45.74, 7.32, 585);

        counts(inferred, 17, 65, 10);
        assertThat(tomatoTransplant(inferred)).isEqualTo("5-6");
        assertThat(window(inferred, "potato", "PLANT", 3)).isEqualTo("4-5");
        assertThat(window(inferred, "potato", "HARVEST", 6)).isEqualTo("7-8");
        assertThat(window(inferred, "basil", "SOW", 3)).isEqualTo("5-7");
        assertThat(window(inferred, "basil", "HARVEST", 6)).isEqualTo("8-10");
        assertThat(window(inferred, "garlic", "PLANT", 10)).isEqualTo("9-11");
        assertThat(window(inferred, "spinach", "HARVEST", 11)).isEqualTo("not derived");
        assertThat(window(inferred, "artichoke", "HARVEST", 11)).isEqualTo("not derived");
    }

    @Test
    void lyonMovesLikeTheOtherNorthernPlains() {
        List<InferredWindow> inferred = at(45.76, 4.84, 171);

        counts(inferred, 77, 15, 0);
        assertThat(tomatoTransplant(inferred)).isEqualTo("4-5");
        assertThat(window(inferred, "basil", "SOW", 3)).isEqualTo("4-6");
        assertThat(window(inferred, "cabbage", "HARVEST", 6)).isEqualTo("6-11");
    }

    @Test
    void yearRoundWindowsNeverMove() {
        WindowRow yearRound = new WindowRow("tomato", "CENTRO", "HARVEST", 1, 12, "", "rootology-editorial", false);
        SiteProfile cold = new SiteProfile(null, 45.1, 7.7, 243, 205, 0.98, 96, 313, 4, 10,
                SiteProfile.Correction.APPLIED, List.of("agera5"));

        assertThat(FrostShiftRule.infer(List.of(yearRound), GROUPS, cold)).singleElement()
                .satisfies(i -> assertThat(i.window().fromMonth() + "-" + i.window().toMonth()).isEqualTo("1-12"));
    }

    @Test
    void nothingMovesEarlierInSpringForTheTenderHardyAndLongCrops() {
        for (List<InferredWindow> inferred : List.of(at(45.07, 7.69, 205), at(45.74, 7.32, 585),
                at(45.46, 9.19, 108))) {
            for (InferredWindow i : inferred) {
                String group = GROUPS.get(i.from().cropId());
                if (i.from().fromMonth() <= 7 && (group.equals("TENDER") || group.equals("HARDY")
                        || (group.equals("LONG") && !i.from().activity().equals("HARVEST")))) {
                    assertThat(i.window().fromMonth())
                            .as(i.from().cropId() + " " + i.from().activity() + " in spring")
                            .isGreaterThanOrEqualTo(i.from().fromMonth());
                }
            }
        }
    }

    @Test
    void aWindowWhoseEndFallsBeforeItsStartIsNotDerived() {
        WindowRow sowing = new WindowRow("lettuce", "CENTRO", "SOW", 5, 6, "", "rootology-editorial", false);
        SiteProfile dry = new SiteProfile(null, 45.1, 7.7, 243, 205, 0.98, 96, 300, 4, 1,
                SiteProfile.Correction.APPLIED, List.of("agera5"));

        assertThat(FrostShiftRule.infer(List.of(sowing), GROUPS, dry)).isEmpty();
    }
}
