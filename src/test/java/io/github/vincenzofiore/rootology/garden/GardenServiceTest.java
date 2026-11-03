package io.github.vincenzofiore.rootology.garden;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlantingSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlotSummary;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GardenServiceTest {

    @Autowired
    GardenService gardens;

    @Autowired
    SpaceService spaces;

    private long spaceOf(String login) {
        spaces.signedIn(login);
        return spaces.spacesOf(login).getFirst().id();
    }

    @Test
    void aMemberDescribesAGardenWithPlotsAndCrops() {
        long space = spaceOf("gina");
        GardenSummary terrace = gardens.saveGarden(space, "gina", null, "Terrace", "Roma",
                new BigDecimal("41.902782"), new BigDecimal("12.496366"));
        PlotSummary pot = gardens.savePlot(space, "gina", terrace.id(), null, "Big pot", PlotKind.CONTAINER,
                Soil.POTTING_MIX, new BigDecimal("0.5"));
        gardens.plant(space, "gina", pot.id(), "tomato", PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 4, 20), null);
        gardens.plant(space, "gina", pot.id(), "basil", PlantingMethod.SOWN, LocalDate.of(2026, 4, 25), "next to the tomato");

        assertThat(terrace.latitude()).isEqualByComparingTo("41.90");
        assertThat(terrace.longitude()).isEqualByComparingTo("12.50");
        assertThat(gardens.plots(space, "gina", terrace.id())).extracting(PlotSummary::name).containsExactly("Big pot");
        assertThat(gardens.plantings(space, "gina", pot.id())).extracting(PlantingSummary::cropName)
                .containsExactly("Basilico", "Pomodoro");
    }

    @Test
    void growingOnLeavesOutWhatIsHarvestedOrNotYetPlanted() {
        long space = spaceOf("hugo");
        GardenSummary garden = gardens.saveGarden(space, "hugo", null, "Allotment", null, null, null);
        PlotSummary bed = gardens.savePlot(space, "hugo", garden.id(), null, "Bed A", PlotKind.GROUND, Soil.LOAMY, null);
        PlantingSummary peas = gardens.plant(space, "hugo", bed.id(), "pea", PlantingMethod.SOWN, LocalDate.of(2026, 2, 1), null);
        gardens.plant(space, "hugo", bed.id(), "zucchini", PlantingMethod.SOWN, LocalDate.of(2026, 5, 1), null);
        gardens.endPlanting(space, "hugo", peas.id(), LocalDate.of(2026, 5, 20));

        assertThat(gardens.growingOn(space, "hugo", LocalDate.of(2026, 4, 1))).extracting(PlantingSummary::cropId)
                .containsExactly("pea");
        assertThat(gardens.growingOn(space, "hugo", LocalDate.of(2026, 6, 1))).extracting(PlantingSummary::cropId)
                .containsExactly("zucchini");
    }

    @Test
    void anotherSpaceNeitherSeesNorChangesTheGarden() {
        long mine = spaceOf("ines");
        long theirs = spaceOf("jack");
        GardenSummary garden = gardens.saveGarden(mine, "ines", null, "Mine", null, null, null);
        PlotSummary bed = gardens.savePlot(mine, "ines", garden.id(), null, "Bed", PlotKind.GROUND, Soil.CLAY, null);

        assertThat(gardens.gardens(theirs, "jack")).isEmpty();
        assertThatThrownBy(() -> gardens.gardens(mine, "jack")).isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> gardens.plots(theirs, "jack", garden.id())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.plant(theirs, "jack", bed.id(), "tomato", PlantingMethod.SOWN,
                LocalDate.now(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.deletePlot(theirs, "jack", bed.id())).isInstanceOf(IllegalArgumentException.class);
        assertThat(gardens.plots(mine, "ines", garden.id())).hasSize(1);
    }

    @Test
    void refusesInvalidInput() {
        long space = spaceOf("kara");
        GardenSummary garden = gardens.saveGarden(space, "kara", null, "Garden", null, null, null);
        PlotSummary bed = gardens.savePlot(space, "kara", garden.id(), null, "Bed", PlotKind.GROUND, Soil.SANDY, null);

        assertThatThrownBy(() -> gardens.saveGarden(space, "kara", null, " ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.saveGarden(space, "kara", null, "X", null, new BigDecimal("95"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.plant(space, "kara", bed.id(), "lavender", PlantingMethod.SOWN,
                LocalDate.now(), null)).isInstanceOf(IllegalArgumentException.class);
        PlantingSummary carrots = gardens.plant(space, "kara", bed.id(), "carrot", PlantingMethod.SOWN,
                LocalDate.of(2026, 3, 1), null);
        assertThatThrownBy(() -> gardens.endPlanting(space, "kara", carrots.id(), LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
