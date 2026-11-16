package app.rootology.ui;

import app.rootology.garden.PlotKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Where an area goes when it is put on the plan (#90): its default size, and the first free spot for it. */
class PlanPlacementTest {

    @Test
    void anAreaWithoutADeclaredSurfaceTakesTheSizeOfItsKind() {
        assertThat(PlanPlacement.defaultSize(PlotKind.POT, null, 8, 4))
                .isEqualTo(new PlanPlacement.Box(0, 0, 0.4, 0.4));
        assertThat(PlanPlacement.defaultSize(PlotKind.PLANTER, null, 8, 4))
                .isEqualTo(new PlanPlacement.Box(0, 0, 1, 0.4));
        assertThat(PlanPlacement.defaultSize(PlotKind.RAISED_BED, null, 8, 4))
                .isEqualTo(new PlanPlacement.Box(0, 0, 1.2, 0.8));
        assertThat(PlanPlacement.defaultSize(PlotKind.GREENHOUSE, null, 8, 4))
                .isEqualTo(new PlanPlacement.Box(0, 0, 3, 2));
        assertThat(PlanPlacement.defaultSize(PlotKind.GROUND, null, 8, 4))
                .isEqualTo(new PlanPlacement.Box(0, 0, 2, 1));
    }

    @Test
    void aDeclaredSurfaceMakesASquareRoundedToTheDecimetre() {
        PlanPlacement.Box four = PlanPlacement.defaultSize(PlotKind.GROUND, new BigDecimal("4"), 8, 4);
        assertThat(four.width()).isEqualTo(2);
        assertThat(four.depth()).isEqualTo(2);

        // √1.3 is 1.140…: a decimetre is the step the plan moves in.
        PlanPlacement.Box small = PlanPlacement.defaultSize(PlotKind.GROUND, new BigDecimal("1.3"), 8, 4);
        assertThat(small.width()).isEqualTo(1.1);
        assertThat(small.depth()).isEqualTo(1.1);
    }

    @Test
    void aSizeNeverGoesOutsideThePlan() {
        PlanPlacement.Box wide = PlanPlacement.defaultSize(PlotKind.GROUND, null, 1, 0.5);
        assertThat(wide.width()).as("a garden plan one metre wide").isEqualTo(1);
        assertThat(wide.depth()).isEqualTo(0.5);

        PlanPlacement.Box tiny = PlanPlacement.defaultSize(PlotKind.GREENHOUSE, new BigDecimal("0.001"), 8, 4);
        assertThat(tiny.width()).as("never thinner than a pot").isEqualTo(0.1);
        assertThat(tiny.depth()).isEqualTo(0.1);
    }

    @Test
    void theFirstFreeSpotScansFromTheTopLeftAndNeverOverlaps() {
        PlanPlacement.Box size = new PlanPlacement.Box(0, 0, 1, 1);
        assertThat(PlanPlacement.firstFree(size, List.of(), 8, 4))
                .as("an empty plan takes the top-left corner").isEqualTo(new PlanPlacement.Box(0, 0, 1, 1));

        List<PlanPlacement.Box> one = List.of(new PlanPlacement.Box(0, 0, 2, 1));
        assertThat(PlanPlacement.firstFree(size, one, 8, 4))
                .as("beside the first area, on the same row").isEqualTo(new PlanPlacement.Box(2, 0, 1, 1));

        List<PlanPlacement.Box> row = List.of(new PlanPlacement.Box(0, 0, 2, 4), new PlanPlacement.Box(2, 0, 2, 4),
                new PlanPlacement.Box(4, 0, 2, 1), new PlanPlacement.Box(6, 0, 2, 1));
        assertThat(PlanPlacement.firstFree(size, row, 8, 4))
                .as("a free spot lower in the second column").isEqualTo(new PlanPlacement.Box(4, 1, 1, 1));

        List<PlanPlacement.Box> touching = List.of(new PlanPlacement.Box(0, 0, 1, 1));
        assertThat(PlanPlacement.firstFree(size, touching, 2, 1))
                .as("touching is beside, not on top").isEqualTo(new PlanPlacement.Box(1, 0, 1, 1));
    }

    @Test
    void withNoFreeSpotTheAreaGoesToTheCornerAndThePersonMovesIt() {
        List<PlanPlacement.Box> full = List.of(new PlanPlacement.Box(0, 0, 2, 1));
        assertThat(PlanPlacement.firstFree(new PlanPlacement.Box(0, 0, 1, 1), full, 2, 1))
                .isEqualTo(new PlanPlacement.Box(0, 0, 1, 1));
    }

    @Test
    void aSpotIsAlwaysOnTheDecimetreGridAndInsideThePlan() {
        PlanPlacement.Box tiny = new PlanPlacement.Box(0, 0, 0.1, 0.1);
        List<PlanPlacement.Box> taken = List.of(new PlanPlacement.Box(0, 0, 7.9, 0.1));
        PlanPlacement.Box spot = PlanPlacement.firstFree(tiny, taken, 8, 0.1);
        assertThat(spot.x()).as("the first decimetre that is free").isEqualTo(7.9);
        assertThat(spot.width()).isEqualTo(0.1);
    }

    @Test
    void anAreaBiggerThanThePlanIsCutToIt() {
        PlanPlacement.Box size = new PlanPlacement.Box(0, 0, 9, 9);
        assertThat(PlanPlacement.firstFree(size, List.of(), 3, 2))
                .isEqualTo(new PlanPlacement.Box(0, 0, 3, 2));
    }
}
