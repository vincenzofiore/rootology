package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.WindowRow;
import io.github.vincenzofiore.rootology.facts.ZoneShiftRule.InferredWindow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneShiftRuleTest {

    private static WindowRow centro(String crop, String activity, int from, int to) {
        return new WindowRow(crop, "CENTRO", activity, from, to, "", "rootology-editorial", false);
    }

    private static List<String> derived(List<InferredWindow> inferred) {
        return inferred.stream().map(i -> i.window().cropId() + " " + i.window().zone() + " " + i.window().activity()
                + " " + i.window().fromMonth() + "-" + i.window().toMonth()).toList();
    }

    @Test
    void springWorkComesLaterInTheNorthAndEarlierInTheSouth() {
        assertThat(derived(ZoneShiftRule.infer(List.of(centro("tomato", "TRANSPLANT", 4, 5),
                centro("tomato", "HARVEST", 7, 9)))))
                .containsExactly("tomato NORD TRANSPLANT 5-6", "tomato SUD TRANSPLANT 3-4",
                        "tomato NORD HARVEST 8-10", "tomato SUD HARVEST 6-8");
    }

    @Test
    void autumnSowingComesEarlierInTheNorthAndWrapsOverTheYear() {
        assertThat(derived(ZoneShiftRule.infer(List.of(centro("garlic", "PLANT", 10, 12)))))
                .containsExactly("garlic NORD PLANT 9-11", "garlic SUD PLANT 11-1");
    }

    @Test
    void winterHarvestsAreNotDerived() {
        assertThat(ZoneShiftRule.infer(List.of(centro("cauliflower", "HARVEST", 10, 2)))).isEmpty();
    }

    @Test
    void aCuratedWindowAlwaysWins() {
        List<WindowRow> curated = List.of(centro("broad-bean", "SOW", 10, 11),
                new WindowRow("broad-bean", "NORD", "SOW", 2, 2, "", "rootology-editorial", false));

        assertThat(derived(ZoneShiftRule.infer(curated))).containsExactly("broad-bean SUD SOW 11-12");
    }

    @Test
    void derivedWindowsKeepTheirSourceAndAreNeverVerified() {
        InferredWindow inferred = ZoneShiftRule.infer(List.of(centro("basil", "SOW", 3, 5))).getFirst();

        assertThat(inferred.window().source()).isEqualTo("rootology-editorial");
        assertThat(inferred.window().verified()).isFalse();
        assertThat(inferred.from().zone()).isEqualTo("CENTRO");
    }
}
