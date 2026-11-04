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
    void zonesAndCalendarsApplyToGardensInItalyOnly() {
        long abroad = spaceOf("lea");
        gardens.saveGarden(abroad, "lea", null, "Allotment", "Lyon", new BigDecimal("45.76"), new BigDecimal("4.84"), "FR");
        long legacy = spaceOf("max");
        gardens.saveGarden(legacy, "max", null, "Balcony", "Milano", new BigDecimal("45.46"), new BigDecimal("9.19"));

        assertThat(gardens.zone(abroad, "lea")).contains(GardenService.OUTSIDE_ITALY);
        assertThat(gardens.zone(legacy, "max")).as("no country: saved before #30, in Italy").contains("NORD");
        assertThat(gardens.gardens(abroad, "lea").getFirst().countryCode()).isEqualTo("FR");
        assertThatThrownBy(() -> gardens.saveGarden(abroad, "lea", null, "Bad", null, null, null, "France"))
                .isInstanceOf(IllegalArgumentException.class);
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
    void warnsWhenACropFollowsOneOfTheSameFamilyInThePlot() {
        long space = spaceOf("lena");
        GardenSummary garden = gardens.saveGarden(space, "lena", null, "Garden", null, null, null);
        PlotSummary bed = gardens.savePlot(space, "lena", garden.id(), null, "Bed C", PlotKind.GROUND, Soil.LOAMY, null);
        LocalDate june = LocalDate.of(2026, 6, 1);

        assertThat(gardens.rotationWarning(space, "lena", bed.id(), "potato", june)).isEmpty();
        gardens.plant(space, "lena", bed.id(), "tomato", PlantingMethod.TRANSPLANTED, LocalDate.of(2026, 4, 20), null);

        assertThat(gardens.rotationWarning(space, "lena", bed.id(), "potato", june)).hasValueSatisfying(w -> {
            assertThat(w.rule()).isEqualTo(RotationRule.ID);
            assertThat(w.family()).isEqualTo("Solanaceae");
            assertThat(w.previous().cropId()).isEqualTo("tomato");
        });
        assertThat(gardens.rotationWarning(space, "lena", bed.id(), "bean", june)).isEmpty();
        assertThat(gardens.rotationWarning(space, "lena", bed.id(), "potato", LocalDate.of(2026, 4, 1))).isEmpty();
    }

    @Test
    void theZoneFollowsTheGardensLatitude() {
        assertThat(GardenService.zoneOf(45.46)).isEqualTo("NORD");
        assertThat(GardenService.zoneOf(41.90)).isEqualTo("CENTRO");
        assertThat(GardenService.zoneOf(38.12)).isEqualTo("SUD");
        long space = spaceOf("mia");
        assertThat(gardens.zone(space, "mia")).isEmpty();
        gardens.saveGarden(space, "mia", null, "Garden", "Palermo", new BigDecimal("38.12"), new BigDecimal("13.36"));
        assertThat(gardens.zone(space, "mia")).contains("SUD");
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
