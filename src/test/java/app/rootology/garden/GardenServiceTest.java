package app.rootology.garden;

import app.rootology.TestcontainersConfiguration;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.spaces.SpaceAccessException;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@RecordApplicationEvents
class GardenServiceTest {

    @Autowired
    ApplicationEvents events;

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
        GardenSummary abroadGarden = gardens.saveGarden(abroad, "lea", null, "Allotment", "Lyon",
                new BigDecimal("45.76"), new BigDecimal("4.84"), "FR");
        long legacy = spaceOf("max");
        GardenSummary legacyGarden = gardens.saveGarden(legacy, "max", null, "Balcony", "Milano",
                new BigDecimal("45.46"), new BigDecimal("9.19"));

        assertThat(GardenService.zone(abroadGarden)).contains(GardenService.OUTSIDE_ITALY);
        assertThat(GardenService.zone(legacyGarden)).as("no country: saved before #30, in Italy").contains("NORD");
        assertThat(gardens.gardens(abroad, "lea").getFirst().countryCode()).isEqualTo("FR");
        assertThatThrownBy(() -> gardens.saveGarden(abroad, "lea", null, "Bad", null, null, null, "France"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPlotKeepsTheExposureTheGardenerDeclaresAndCanClearIt() {
        long space = spaceOf("nino");
        GardenSummary balcony = gardens.saveGarden(space, "nino", null, "Balcony", "Torino", null, null);

        PlotSummary pot = gardens.savePlot(space, "nino", balcony.id(), null, "Pot", PlotKind.CONTAINER,
                Soil.POTTING_MIX, null, Orientation.NW, DirectSun.PART_SHADE, Wind.SHELTERED, 3);

        assertThat(pot.orientation()).isEqualTo(Orientation.NW);
        assertThat(pot.directSun()).isEqualTo(DirectSun.PART_SHADE);
        assertThat(pot.wind()).isEqualTo(Wind.SHELTERED);
        assertThat(pot.floor()).isEqualTo(3);
        assertThat(gardens.plots(space, "nino", balcony.id())).singleElement().satisfies(p -> {
            assertThat(p.orientation()).isEqualTo(Orientation.NW);
            assertThat(p.floor()).isEqualTo(3);
        });

        PlotSummary cleared = gardens.savePlot(space, "nino", balcony.id(), pot.id(), "Pot", PlotKind.CONTAINER,
                Soil.POTTING_MIX, null);
        assertThat(cleared.orientation()).isNull();
        assertThat(cleared.directSun()).isNull();
        assertThat(cleared.wind()).isNull();
        assertThat(cleared.floor()).isNull();
        assertThatThrownBy(() -> gardens.savePlot(space, "nino", balcony.id(), null, "Bad", PlotKind.CONTAINER,
                Soil.POTTING_MIX, null, null, null, null, -1))
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

        assertThat(gardens.growingOn(space, "hugo", garden.id(), LocalDate.of(2026, 4, 1)))
                .extracting(PlantingSummary::cropId).containsExactly("pea");
        assertThat(gardens.growingOn(space, "hugo", garden.id(), LocalDate.of(2026, 6, 1)))
                .extracting(PlantingSummary::cropId).containsExactly("zucchini");
    }

    @Test
    void aGuestSeesTheGardenButChangesNothing() {
        long space = spaceOf("pia");
        spaces.signedIn("quentin");
        spaces.addMember(space, "pia", "quentin", app.rootology.spaces.Role.GUEST);
        GardenSummary garden = gardens.saveGarden(space, "pia", null, "Orto", null, null, null);
        PlotSummary bed = gardens.savePlot(space, "pia", garden.id(), null, "Bed", PlotKind.GROUND, Soil.LOAMY, null);

        assertThat(gardens.gardens(space, "quentin")).extracting(GardenSummary::name).containsExactly("Orto");
        assertThat(gardens.plots(space, "quentin", garden.id())).hasSize(1);
        assertThatThrownBy(() -> gardens.saveGarden(space, "quentin", null, "Mine", null, null, null))
                .isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> gardens.deletePlot(space, "quentin", bed.id())).isInstanceOf(SpaceAccessException.class);
        assertThatThrownBy(() -> gardens.plant(space, "quentin", bed.id(), "tomato", PlantingMethod.SOWN,
                LocalDate.of(2026, 4, 1), null)).isInstanceOf(SpaceAccessException.class);
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
        GardenSummary noCoordinates = gardens.saveGarden(space, "mia", null, "Garden", "Somewhere", null, null);
        assertThat(GardenService.zone(noCoordinates)).as("without a latitude there is no zone").isEmpty();
        GardenSummary palermo = gardens.saveGarden(space, "mia", null, "Garden", "Palermo",
                new BigDecimal("38.12"), new BigDecimal("13.36"));
        assertThat(GardenService.zone(palermo)).contains("SUD");
    }

    @Test
    void theElevationFollowsThePlaceAndIsKeptWhenThePlaceDoesNotChange() {
        long space = spaceOf("otto");
        GardenSummary garden = gardens.saveGarden(space, "otto", null, "Orto", "Torino",
                new BigDecimal("45.07"), new BigDecimal("7.69"), "IT", 205);
        assertThat(garden.elevationM()).isEqualTo(205);

        GardenSummary renamed = gardens.saveGarden(space, "otto", garden.id(), "Orto rinomato", "Torino",
                new BigDecimal("45.07"), new BigDecimal("7.69"), "IT");
        assertThat(renamed.elevationM()).as("the place did not change").isEqualTo(205);

        GardenSummary moved = gardens.saveGarden(space, "otto", garden.id(), "Orto", "Milano",
                new BigDecimal("45.46"), new BigDecimal("9.19"), "IT");
        assertThat(moved.elevationM()).as("a new place has no elevation until it is looked up again").isNull();

        GardenSummary cleared = gardens.saveGarden(space, "otto", garden.id(), "Orto", "Milano", null, null,
                "IT", 108);
        assertThat(cleared.elevationM()).as("no coordinates, no elevation").isNull();
    }

    @Test
    void aPlotIsLookedUpInOneGardenOnlyAndAmbiguityIsReported() {
        long space = spaceOf("olga");
        GardenSummary first = gardens.saveGarden(space, "olga", null, "Balcony", null, null, null);
        GardenSummary second = gardens.saveGarden(space, "olga", null, "Allotment", null, null, null);
        PlotSummary bedA = gardens.savePlot(space, "olga", first.id(), null, "Aiuola A", PlotKind.GROUND, Soil.LOAMY, null);
        gardens.savePlot(space, "olga", first.id(), null, "Aiuola B", PlotKind.GROUND, Soil.LOAMY, null);

        assertThat(gardens.plotNamed(space, "olga", first.id(), "aiuola a").plot()).isEqualTo(bedA);
        assertThat(gardens.plotNamed(space, "olga", first.id(), "A").plot())
                .as("a unique partial match still works").isEqualTo(bedA);
        assertThat(gardens.plotNamed(space, "olga", first.id(), "Aiuola")).as("two plots match: never the first")
                .satisfies(lookup -> {
                    assertThat(lookup.found()).isFalse();
                    assertThat(lookup.matches()).containsExactly("Aiuola A", "Aiuola B");
                });
        assertThat(gardens.plotNamed(space, "olga", second.id(), "Aiuola A").found())
                .as("another garden's plot is not a match").isFalse();
        assertThat(gardens.plotNamed(space, "olga", first.id(), "Nowhere").found()).isFalse();
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

    /** A new place is announced so its forecast can be fetched (#67); a save that keeps the place is not. */
    @Test
    void aGardenThatGetsANewPlaceIsAnnounced() {
        long space = spaceOf("ugo");
        GardenSummary created = gardens.saveGarden(space, "ugo", null, "Orto", "Vasto",
                new BigDecimal("42.11"), new BigDecimal("14.71"));
        gardens.saveGarden(space, "ugo", created.id(), "Orto grande", "Vasto",
                new BigDecimal("42.11"), new BigDecimal("14.71"));
        gardens.saveGarden(space, "ugo", created.id(), "Orto grande", "Trivento",
                new BigDecimal("41.78"), new BigDecimal("14.55"));
        gardens.saveGarden(space, "ugo", null, "Senza posto", null, null, null);

        assertThat(events.stream(GardenLocated.class)).extracting(GardenLocated::latitude)
                .containsExactly(new BigDecimal("42.11"), new BigDecimal("41.78"));
    }
}
