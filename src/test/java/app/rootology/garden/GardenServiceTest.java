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
        return app.rootology.TestSpaces.personal(spaces, login);
    }

    @Test
    void aMemberDescribesAGardenWithPlotsAndCrops() {
        long space = spaceOf("gina");
        GardenSummary terrace = gardens.saveGarden(space, "gina", null, "Terrace", "Roma",
                new BigDecimal("41.902782"), new BigDecimal("12.496366"));
        PlotSummary pot = gardens.savePlot(space, "gina", terrace.id(), null, "Big pot", PlotKind.POT,
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

        PlotSummary pot = gardens.savePlot(space, "nino", balcony.id(), null, "Pot", PlotKind.POT,
                Soil.POTTING_MIX, null, Orientation.NW, DirectSun.PART_SHADE, Wind.SHELTERED, 3);

        assertThat(pot.orientation()).isEqualTo(Orientation.NW);
        assertThat(pot.directSun()).isEqualTo(DirectSun.PART_SHADE);
        assertThat(pot.wind()).isEqualTo(Wind.SHELTERED);
        assertThat(pot.floor()).isEqualTo(3);
        assertThat(gardens.plots(space, "nino", balcony.id())).singleElement().satisfies(p -> {
            assertThat(p.orientation()).isEqualTo(Orientation.NW);
            assertThat(p.floor()).isEqualTo(3);
        });

        PlotSummary cleared = gardens.savePlot(space, "nino", balcony.id(), pot.id(), "Pot", PlotKind.POT,
                Soil.POTTING_MIX, null);
        assertThat(cleared.orientation()).isNull();
        assertThat(cleared.directSun()).isNull();
        assertThat(cleared.wind()).isNull();
        assertThat(cleared.floor()).isNull();
        assertThatThrownBy(() -> gardens.savePlot(space, "nino", balcony.id(), null, "Bad", PlotKind.POT,
                Soil.POTTING_MIX, null, null, null, null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSunHoursAreStoredWholeAndRefusedOutsideARealDay() {
        long space = spaceOf("nino-sun");
        GardenSummary balcony = gardens.saveGarden(space, "nino-sun", null, "Balcony", "Torino", null, null);

        PlotSummary pot = gardens.savePlot(space, "nino-sun", balcony.id(), null, "Pot", PlotKind.POT,
                Soil.POTTING_MIX, null, Orientation.S, null, null, null, 7, 2);

        assertThat(pot.sunHoursSummer()).isEqualTo(7);
        assertThat(pot.sunHoursWinter()).isEqualTo(2);
        assertThat(pot.directSunIn(java.time.Month.JUNE)).isEqualTo(DirectSun.FULL_SUN);
        assertThat(pot.directSunIn(java.time.Month.DECEMBER)).isEqualTo(DirectSun.SHADE);
        assertThat(gardens.plots(space, "nino-sun", balcony.id())).singleElement().satisfies(p -> {
            assertThat(p.sunHoursSummer()).isEqualTo(7);
            assertThat(p.sunHoursWinter()).isEqualTo(2);
        });

        // The bounds themselves are real: a whole day at most, and a season with no sun is a value.
        PlotSummary extremes = gardens.savePlot(space, "nino-sun", balcony.id(), pot.id(), "Pot", PlotKind.POT,
                Soil.POTTING_MIX, null, Orientation.S, null, null, null, 0, 16);
        assertThat(extremes.sunHoursSummer()).isZero();
        assertThat(extremes.sunHoursWinter()).isEqualTo(16);
        assertThatThrownBy(() -> gardens.savePlot(space, "nino-sun", balcony.id(), pot.id(), "Pot", PlotKind.POT,
                Soil.POTTING_MIX, null, Orientation.S, null, null, null, -1, 3))
                .as("a negative number of hours is refused").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gardens.savePlot(space, "nino-sun", balcony.id(), pot.id(), "Pot", PlotKind.POT,
                Soil.POTTING_MIX, null, Orientation.S, null, null, null, 3, 17))
                .as("more hours than a day has is refused").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whoDeclaredTheSunAndWhenFollowTheValueAndNotAMereRename() {
        long space = spaceOf("olmo");
        spaces.signedIn("pino");
        spaces.addMember(space, "olmo", "pino", app.rootology.spaces.Role.MEMBER);
        GardenSummary balcony = gardens.saveGarden(space, "olmo", null, "Balcony", "Torino", null, null);
        PlotSummary pot = gardens.savePlot(space, "olmo", balcony.id(), null, "Pot", PlotKind.POT,
                Soil.POTTING_MIX, null, Orientation.S, null, null, null, 7, 2);

        assertThat(pot.sunDeclaredBy()).isEqualTo("olmo");
        assertThat(pot.sunDeclaredAt()).isNotNull();
        java.time.Instant firstDeclaration = pot.sunDeclaredAt();

        PlotSummary renamed = gardens.savePlot(space, "olmo", balcony.id(), pot.id(), "Vaso grande", PlotKind.POT,
                Soil.POTTING_MIX, null, Orientation.S, null, null, null);
        assertThat(renamed.name()).isEqualTo("Vaso grande");
        assertThat(renamed.sunHoursSummer()).as("the 12-argument save keeps the hours").isEqualTo(7);
        assertThat(renamed.sunHoursWinter()).isEqualTo(2);
        assertThat(renamed.sunDeclaredBy()).as("a rename declares nothing").isEqualTo("olmo");
        assertThat(renamed.sunDeclaredAt()).isEqualTo(firstDeclaration);

        PlotSummary hoursChanged = gardens.savePlot(space, "pino", balcony.id(), pot.id(), "Vaso grande",
                PlotKind.POT, Soil.POTTING_MIX, null, Orientation.S, null, null, null, 5, 2);
        assertThat(hoursChanged.sunDeclaredBy()).as("the hours changed: the new declarer is recorded").isEqualTo("pino");
        assertThat(hoursChanged.sunDeclaredAt()).isAfterOrEqualTo(firstDeclaration);

        PlotSummary levelChanged = gardens.savePlot(space, "olmo", balcony.id(), pot.id(), "Vaso grande",
                PlotKind.POT, Soil.POTTING_MIX, null, Orientation.S, DirectSun.PART_SHADE, null, null, 5, 2);
        assertThat(levelChanged.sunDeclaredBy()).as("the level changed: it is a declaration too").isEqualTo("olmo");
        assertThat(levelChanged.sunDeclaredAt()).isAfterOrEqualTo(hoursChanged.sunDeclaredAt());
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
    void aHarvestCanBeUndoneAndOnlyByAContributor() {
        long space = spaceOf("nora");
        spaces.signedIn("gisella");
        spaces.addMember(space, "nora", "gisella", app.rootology.spaces.Role.GUEST);
        GardenSummary garden = gardens.saveGarden(space, "nora", null, "Orto", null, null, null);
        PlotSummary bed = gardens.savePlot(space, "nora", garden.id(), null, "Area", PlotKind.GROUND, Soil.LOAMY, null);
        PlantingSummary beans = gardens.plant(space, "nora", bed.id(), "bean", PlantingMethod.SOWN,
                LocalDate.of(2026, 4, 1), null);
        gardens.endPlanting(space, "nora", beans.id(), LocalDate.of(2026, 7, 1));

        assertThatThrownBy(() -> gardens.undoEndPlanting(space, "gisella", beans.id()))
                .isInstanceOf(SpaceAccessException.class);

        gardens.undoEndPlanting(space, "nora", beans.id());

        assertThat(gardens.growingOn(space, "nora", garden.id(), LocalDate.of(2026, 7, 2)))
                .extracting(PlantingSummary::cropId).containsExactly("bean");
        assertThat(gardens.plantings(space, "nora", bed.id()))
                .extracting(PlantingSummary::endedOn).containsExactly((LocalDate) null);
    }

    @Test
    void aPlantingEnteredByMistakeIsDeletedAndOnlyByAContributor() {
        long space = spaceOf("rosa");
        spaces.signedIn("sergio");
        spaces.addMember(space, "rosa", "sergio", app.rootology.spaces.Role.GUEST);
        GardenSummary garden = gardens.saveGarden(space, "rosa", null, "Orto", null, null, null);
        PlotSummary bed = gardens.savePlot(space, "rosa", garden.id(), null, "Area", PlotKind.GROUND, Soil.LOAMY, null);
        PlantingSummary mistake = gardens.plant(space, "rosa", bed.id(), "bean", PlantingMethod.SOWN,
                LocalDate.of(2026, 4, 1), null);

        assertThatThrownBy(() -> gardens.deletePlanting(space, "sergio", mistake.id()))
                .isInstanceOf(SpaceAccessException.class);

        gardens.deletePlanting(space, "rosa", mistake.id());

        assertThat(gardens.plantings(space, "rosa", bed.id())).isEmpty();
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
    void perennialsAreLeftOutOfTheRotation() {
        long space = spaceOf("mara");
        GardenSummary garden = gardens.saveGarden(space, "mara", null, "Garden", null, null, null);
        PlotSummary lettuceBed = gardens.savePlot(space, "mara", garden.id(), null, "Bed D", PlotKind.GROUND, Soil.LOAMY, null);
        PlotSummary artichokeBed = gardens.savePlot(space, "mara", garden.id(), null, "Bed E", PlotKind.GROUND, Soil.LOAMY, null);
        LocalDate june = LocalDate.of(2026, 6, 1);
        // Lettuce and artichoke are both Asteraceae; the artichoke stays in place for years.
        gardens.plant(space, "mara", lettuceBed.id(), "lettuce", PlantingMethod.SOWN, LocalDate.of(2026, 3, 1), null);
        gardens.plant(space, "mara", artichokeBed.id(), "artichoke", PlantingMethod.TRANSPLANTED, LocalDate.of(2025, 9, 1), null);

        assertThat(gardens.rotationWarning(space, "mara", lettuceBed.id(), "artichoke", june))
                .as("a perennial candidate").isEmpty();
        assertThat(gardens.rotationWarning(space, "mara", artichokeBed.id(), "lettuce", june))
                .as("beside a perennial still growing, not after it").isEmpty();
        assertThat(gardens.rotationWarning(space, "mara", lettuceBed.id(), "lettuce", june))
                .as("annuals keep the rule").isPresent();
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
        assertThatThrownBy(() -> gardens.plant(space, "kara", bed.id(), "papaya", PlantingMethod.SOWN,
                LocalDate.now(), null)).isInstanceOf(IllegalArgumentException.class);
        PlantingSummary carrots = gardens.plant(space, "kara", bed.id(), "carrot", PlantingMethod.SOWN,
                LocalDate.of(2026, 3, 1), null);
        assertThatThrownBy(() -> gardens.endPlanting(space, "kara", carrots.id(), LocalDate.of(2026, 2, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theRegionFollowsThePlaceAndIsClearedWhenItMovesOrLosesItsCoordinates() {
        long space = spaceOf("region");
        GardenSummary garden = gardens.saveGarden(space, "region", null, "Orto", "Vasto",
                new BigDecimal("42.11"), new BigDecimal("14.71"), "IT");
        gardens.setRegion(garden.id(), "13");
        assertThat(gardens.garden(space, "region", garden.id()).orElseThrow().regionCode()).isEqualTo("13");

        GardenSummary same = gardens.saveGarden(space, "region", garden.id(), "Orto", "Vasto",
                new BigDecimal("42.11"), new BigDecimal("14.71"), "IT");
        assertThat(same.regionCode()).as("the same place keeps the region").isEqualTo("13");

        GardenSummary moved = gardens.saveGarden(space, "region", garden.id(), "Orto", "Trivento",
                new BigDecimal("41.78"), new BigDecimal("14.55"), "IT");
        assertThat(moved.regionCode()).as("a moved garden loses the region of its old place").isNull();

        gardens.setRegion(garden.id(), "13");
        GardenSummary cleared = gardens.saveGarden(space, "region", garden.id(), "Orto", "Vasto", null, null, "IT");
        assertThat(cleared.regionCode()).as("cleared coordinates lose the region").isNull();

        assertThatThrownBy(() -> gardens.setRegion(garden.id(), "Lombardia"))
                .as("a region is a GeoNames code, not a name").isInstanceOf(IllegalArgumentException.class);
        gardens.setRegion(garden.id(), null);
        assertThat(gardens.garden(space, "region", garden.id()).orElseThrow().regionCode()).isNull();
    }

    @Test
    void withoutRegionListsOnlyItalianGardensWithCoordinatesAndNoRegion() {
        long space = spaceOf("regions");
        GardenSummary italian = gardens.saveGarden(space, "regions", null, "Italiano", "Vasto",
                new BigDecimal("42.11"), new BigDecimal("14.71"), "IT");
        GardenSummary known = gardens.saveGarden(space, "regions", null, "Con regione", "Vasto",
                new BigDecimal("42.12"), new BigDecimal("14.72"), "IT");
        gardens.setRegion(known.id(), "13");
        GardenSummary foreign = gardens.saveGarden(space, "regions", null, "Straniero", "Lyon",
                new BigDecimal("45.76"), new BigDecimal("4.84"), "FR");
        GardenSummary nowhere = gardens.saveGarden(space, "regions", null, "Senza posto", "Vasto", null, null, "IT");

        assertThat(gardens.withoutRegion()).extracting(GardenSummary::id)
                .contains(italian.id())
                .doesNotContain(known.id(), foreign.id(), nowhere.id());
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
