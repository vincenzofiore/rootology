package app.rootology.ui;

import app.rootology.garden.DirectSun;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.garden.Orientation;
import app.rootology.garden.PlantingMethod;
import app.rootology.garden.PlotKind;
import app.rootology.garden.Soil;
import app.rootology.garden.Wind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The four first steps of a garden (#81), read from what the garden home already knows. */
class FirstStepsTest {

    private static final GardenSummary LOCATED = garden(new BigDecimal("45.46"), new BigDecimal("9.19"));
    private static final GardenSummary UNLOCATED = garden(null, null);
    private static final PlotSummary PLOT = new PlotSummary(1, 1, "Aiuola 1", PlotKind.GROUND, Soil.UNKNOWN,
            null, null, null, null, null);
    private static final PlantingSummary PLANTING = new PlantingSummary(1, 1, "Aiuola 1", PlotKind.GROUND, "tomato", "Pomodoro",
            PlantingMethod.TRANSPLANTED, LocalDate.now(), null, null);

    @Test
    void aNewGardenHasEveryStepToDo() {
        FirstSteps steps = FirstSteps.of(UNLOCATED, List.of(), List.of(), false);

        assertThat(steps.located()).isFalse();
        assertThat(steps.hasPlot()).isFalse();
        assertThat(steps.hasPlanting()).isFalse();
        assertThat(steps.hasQuestion()).isFalse();
        assertThat(steps.allDone()).isFalse();
    }

    @Test
    void eachStepIsDoneOnItsOwn() {
        assertThat(FirstSteps.of(LOCATED, List.of(), List.of(), false).located()).isTrue();
        assertThat(FirstSteps.of(UNLOCATED, List.of(PLOT), List.of(), false).hasPlot()).isTrue();
        assertThat(FirstSteps.of(UNLOCATED, List.of(), List.of(PLANTING), false).hasPlanting()).isTrue();
        assertThat(FirstSteps.of(UNLOCATED, List.of(), List.of(), true).hasQuestion()).isTrue();
    }

    @Test
    void theCardIsHiddenOnlyWhenEveryStepIsDone() {
        assertThat(FirstSteps.of(LOCATED, List.of(PLOT), List.of(PLANTING), true).allDone()).isTrue();
        assertThat(FirstSteps.of(LOCATED, List.of(PLOT), List.of(PLANTING), false).allDone()).isFalse();
        assertThat(FirstSteps.of(LOCATED, List.of(PLOT), List.of(), true).allDone()).isFalse();
        assertThat(FirstSteps.of(LOCATED, List.of(), List.of(PLANTING), true).allDone()).isFalse();
        assertThat(FirstSteps.of(UNLOCATED, List.of(PLOT), List.of(PLANTING), true).allDone()).isFalse();
    }

    private static GardenSummary garden(BigDecimal latitude, BigDecimal longitude) {
        return new GardenSummary(1, "Orto", "Milano", latitude, longitude, "IT", 120);
    }
}
