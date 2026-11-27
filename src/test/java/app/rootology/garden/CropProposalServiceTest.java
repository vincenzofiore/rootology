package app.rootology.garden;

import app.rootology.TestcontainersConfiguration;
import app.rootology.facts.CropTraits;
import app.rootology.facts.FrostSuitabilityRule;
import app.rootology.garden.CropProposalRule.Goal;
import app.rootology.garden.CropProposalRule.Proposal;
import app.rootology.garden.CropProposalRule.Uncertainty;
import app.rootology.garden.CropProposalService.AreaProposals;
import app.rootology.garden.CropProposalService.CropChecks;
import app.rootology.garden.CropProposalService.Light;
import app.rootology.garden.CropProposalService.Proposals;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.spaces.SpaceAccessException;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * `crop-proposal-v1` on a real garden (#141): the free areas of a garden in Rome in March, drawn from the
 * curated knowledge base, with the site of the pipeline, the light of the shaded area and the rotation rule.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CropProposalServiceTest {

    static final String LOGIN = "cp-vera";

    @Autowired
    CropProposalService proposals;

    @Autowired
    GardenService gardens;

    @Autowired
    SpaceService spaces;

    @Autowired
    CropTraits traits;

    @Autowired
    Clock clock;

    /** A garden in Rome with two free areas (one shaded) and an area where a legume grows. */
    private GardenSummary romeGarden(long space) {
        GardenSummary garden = gardens.saveGarden(space, LOGIN, null, "Rome terrace", "Roma",
                new BigDecimal("41.90"), new BigDecimal("12.50"), "IT");
        PlotSummary sunny = gardens.savePlot(space, LOGIN, garden.id(), null, "Sunny", PlotKind.GROUND,
                Soil.LOAMY, null, Orientation.S, DirectSun.FULL_SUN, Wind.EXPOSED, null);
        gardens.savePlot(space, LOGIN, garden.id(), null, "Shady", PlotKind.GROUND, Soil.LOAMY, null,
                Orientation.N, DirectSun.SHADE, Wind.EXPOSED, null);
        PlotSummary peas = gardens.savePlot(space, LOGIN, garden.id(), null, "Peas", PlotKind.GROUND,
                Soil.LOAMY, null, Orientation.S, DirectSun.FULL_SUN, Wind.EXPOSED, null);
        // Last summer's basil is over: the area is free, and its family stays in the plot's history.
        gardens.plant(space, LOGIN, sunny.id(), "basil", PlantingMethod.SOWN, LocalDate.of(2025, 3, 1), null);
        gardens.endPlanting(space, LOGIN, gardens.plantings(space, LOGIN, sunny.id()).getFirst().id(),
                LocalDate.of(2025, 9, 1));
        gardens.plant(space, LOGIN, peas.id(), "pea", PlantingMethod.SOWN, LocalDate.of(2026, 10, 1), null);
        return garden;
    }

    private long spaceOf(String login) {
        spaces.signedIn(login);
        return app.rootology.TestSpaces.personal(spaces, login);
    }

    private static AreaProposals area(Proposals result, String name) {
        return result.areas().stream().filter(a -> a.areaName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void theFreeAreasOfAGardenInRomeGetCropsForMarch() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeGarden(space);

        Proposals result = proposals.propose(space, LOGIN, garden.id(), Month.MARCH,
                List.of(Goal.VARIETY, Goal.SOIL));

        assertThat(result.month()).isEqualTo(Month.MARCH);
        assertThat(result.siteFromGarden()).as("Rome has its own climate profile").isTrue();
        assertThat(result.areas()).extracting(AreaProposals::areaName)
                .as("only the areas where nothing grows on the first of March")
                .containsExactly("Shady", "Sunny");
        assertThat(area(result, "Sunny").proposals()).extracting(Proposal::cropId)
                .as("annuals before perennials of the same score").containsExactly("beetroot", "borage", "carrot");
        assertThat(area(result, "Shady").proposals()).extracting(Proposal::cropId)
                .containsExactly("phacelia", "sweet-alyssum", "aronia");
    }

    @Test
    void theShadedAreaGetsNoCropThatNeedsFullSun() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeGarden(space);

        Proposals result = proposals.propose(space, LOGIN, garden.id(), Month.MARCH,
                List.of(Goal.VARIETY, Goal.SOIL));

        for (Proposal proposal : area(result, "Shady").proposals()) {
            assertThat(traits.lightOptimum(proposal.cropId()).flatMap(LightRule::needs))
                    .as(proposal.cropId() + " in the shade")
                    .isNotEqualTo(java.util.Optional.of(DirectSun.FULL_SUN));
        }
        assertThat(area(result, "Shady").proposals()).extracting(Proposal::cropId).doesNotContain("pea",
                "chickpea", "beetroot");
    }

    @Test
    void aRotationWarningTakesTheCropOutOfTheProposal() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeGarden(space);
        long sunny = area(proposals.propose(space, LOGIN, garden.id(), Month.MARCH, List.of(Goal.VARIETY)),
                "Sunny").areaId();

        Proposals result = proposals.propose(space, LOGIN, garden.id(), Month.MARCH,
                List.of(Goal.VARIETY, Goal.SOIL));

        assertThat(gardens.rotationWarning(space, LOGIN, sunny, "basil",
                CropProposalService.firstDayOf(Month.MARCH, LocalDate.now(clock))))
                .as("basil follows basil in the sunny area").isPresent();
        assertThat(area(result, "Sunny").proposals()).extracting(Proposal::cropId).doesNotContain("basil");
    }

    @Test
    void aProposalCarriesItsWindowItsSourcesAndItsUncertainties() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeGarden(space);

        Proposal first = area(proposals.propose(space, LOGIN, garden.id(), Month.MARCH,
                List.of(Goal.VARIETY, Goal.SOIL)), "Sunny").proposals().getFirst();

        assertThat(first.cropId()).isEqualTo("beetroot");
        assertThat(first.activity()).isEqualTo("SOW");
        assertThat(first.month()).isEqualTo(3);
        assertThat(first.goals()).containsExactly(Goal.VARIETY);
        assertThat(first.sources()).contains("rootology-editorial", "agera5", "copernicus-dem-90m", "wikidata");
        assertThat(first.uncertainties()).contains(Uncertainty.EDITORIAL_WINDOW);
    }

    @Test
    void aGardenWithoutCoordinatesGetsThePipelinesDefaultSite() {
        long space = spaceOf(LOGIN);
        GardenSummary noMap = gardens.saveGarden(space, LOGIN, null, "No map", "Roma", null, null);
        gardens.savePlot(space, LOGIN, noMap.id(), null, "Bed", PlotKind.GROUND, Soil.LOAMY, null);

        Proposals result = proposals.propose(space, LOGIN, noMap.id(), Month.MARCH, List.of(Goal.VARIETY));

        assertThat(result.siteFromGarden()).isFalse();
        assertThat(result.areas()).singleElement().satisfies(a -> assertThat(a.proposals()).isNotEmpty());
    }

    @Test
    void theCurrentMonthIsTheDefault() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeGarden(space);

        Proposals result = proposals.propose(space, LOGIN, garden.id(), List.of(Goal.VARIETY));

        assertThat(result.month()).isEqualTo(LocalDate.now(clock).getMonth());
    }

    @Test
    void onlyAMemberOfTheSpaceReadsTheProposal() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeGarden(space);

        assertThatThrownBy(() -> proposals.propose(space, "cp-ugo", garden.id(), Month.MARCH,
                List.of(Goal.VARIETY))).isInstanceOf(SpaceAccessException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> proposals.propose(space, LOGIN, 999_999,
                Month.MARCH, List.of(Goal.VARIETY)));
        assertThatIllegalArgumentException().isThrownBy(() -> proposals.propose(space, LOGIN, garden.id(),
                Month.MARCH, List.of()));
    }

    /** A bed of a garden, with the sun the checks read when they care (#185). */
    private long bed(long space, GardenSummary garden, String name, PlotKind kind, DirectSun sun) {
        return gardens.savePlot(space, LOGIN, garden.id(), null, name, kind, Soil.LOAMY, null,
                null, sun, null, null).id();
    }

    private GardenSummary romeChecksGarden(long space) {
        return gardens.saveGarden(space, LOGIN, null, "Checks", "Roma", new BigDecimal("41.90"),
                new BigDecimal("12.50"), "IT");
    }

    @Test
    void aCompanionAndAnAntagonistGrowingInTheAreaAreNamed() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeChecksGarden(space);
        long companions = bed(space, garden, "Onion bed", PlotKind.GROUND, DirectSun.FULL_SUN);
        long antagonists = bed(space, garden, "Potato bed", PlotKind.GROUND, DirectSun.FULL_SUN);
        LocalDate today = LocalDate.now(clock);
        gardens.plant(space, LOGIN, companions, "onion", PlantingMethod.SOWN, today, null);
        gardens.plant(space, LOGIN, antagonists, "potato", PlantingMethod.SOWN, today, null);

        CropChecks carrot = proposals.checks(space, LOGIN, garden.id(), companions, "carrot");
        assertThat(carrot.companionsInArea()).as("carrot and onion are declared companions").containsExactly("onion");
        assertThat(carrot.antagonistsInArea()).isEmpty();

        CropChecks tomato = proposals.checks(space, LOGIN, garden.id(), antagonists, "tomato");
        assertThat(tomato.antagonistsInArea()).as("tomato and potato are declared antagonists")
                .containsExactly("potato");
        assertThat(tomato.companionsInArea()).isEmpty();

        assertThat(proposals.checks(space, LOGIN, garden.id(), antagonists, "carrot").companionsInArea())
                .as("potato is no companion of carrot").isEmpty();

        gardens.plant(space, LOGIN, antagonists, "carrot", PlantingMethod.SOWN, today, null);
        assertThat(proposals.checks(space, LOGIN, garden.id(), antagonists, "onion").companionsInArea())
                .as("the pair carrot-onion is read both ways").containsExactly("carrot");
    }

    @Test
    void theLightIsUnknownWhenNothingIsDeclaredAndAWarningWhenTheAreaIsTooDark() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeChecksGarden(space);
        long undeclared = bed(space, garden, "Nothing declared", PlotKind.GROUND, null);
        long shade = bed(space, garden, "Shade", PlotKind.GROUND, DirectSun.SHADE);
        long sun = bed(space, garden, "Sun", PlotKind.GROUND, DirectSun.FULL_SUN);

        assertThat(proposals.checks(space, LOGIN, garden.id(), undeclared, "pea").light())
                .as("the area's sun is not known: a question, not a warning").isEqualTo(Light.UNKNOWN);
        assertThat(proposals.checks(space, LOGIN, garden.id(), shade, "pea").light())
                .as("a pea wants full sun").isEqualTo(Light.TOO_DARK);
        assertThat(proposals.checks(space, LOGIN, garden.id(), sun, "pea").light()).isEqualTo(Light.ENOUGH);
        assertThat(proposals.checks(space, LOGIN, garden.id(), sun, "phacelia").light())
                .as("ECOCROP has no light need for phacelia").isEqualTo(Light.UNKNOWN);
    }

    @Test
    void aCropOfTheSameFamilyAsThePreviousOneCarriesTheRotationWarning() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeChecksGarden(space);
        long area = bed(space, garden, "Bed", PlotKind.GROUND, DirectSun.FULL_SUN);
        gardens.plant(space, LOGIN, area, "basil", PlantingMethod.SOWN, LocalDate.of(2025, 3, 1), null);
        gardens.endPlanting(space, LOGIN, gardens.plantings(space, LOGIN, area).getFirst().id(),
                LocalDate.of(2025, 9, 1));

        CropChecks basil = proposals.checks(space, LOGIN, garden.id(), area, "basil");
        assertThat(basil.rotation()).isNotNull();
        assertThat(basil.rotation().family()).isEqualTo("Lamiaceae");
        assertThat(basil.rotation().previous().cropId()).isEqualTo("basil");
        assertThat(proposals.checks(space, LOGIN, garden.id(), area, "carrot").rotation())
                .as("another family follows freely").isNull();
    }

    @Test
    void aCropWithNoWindowInTheMonthIsOutsideIt() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeChecksGarden(space);
        long area = bed(space, garden, "Bed", PlotKind.GROUND, DirectSun.FULL_SUN);

        assertThat(proposals.checks(space, LOGIN, garden.id(), area, "tomato", Month.DECEMBER).inWindow())
                .as("tomatoes go in in spring, not at Christmas").isFalse();
        assertThat(proposals.checks(space, LOGIN, garden.id(), area, "tomato", Month.DECEMBER).facts().windows())
                .isEmpty();
    }

    @Test
    void theChecksAgreeWithTheProposals() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeGarden(space);
        Proposals result = proposals.propose(space, LOGIN, garden.id(), Month.MARCH,
                List.of(Goal.VARIETY, Goal.SOIL));

        for (AreaProposals area : result.areas()) {
            assertThat(area.proposals()).isNotEmpty();
            for (Proposal proposal : area.proposals()) {
                CropChecks checks = proposals.checks(space, LOGIN, garden.id(), area.areaId(),
                        proposal.cropId(), Month.MARCH);
                assertThat(checks.inWindow()).as(proposal.cropId() + " has its window").isTrue();
                assertThat(checks.rotation()).as(proposal.cropId() + " follows another family").isNull();
                assertThat(checks.antagonistsInArea()).as(proposal.cropId() + " has no antagonist").isEmpty();
                assertThat(checks.light()).as(proposal.cropId() + " has enough light")
                        .isNotEqualTo(Light.TOO_DARK);
            }
        }
    }

    @Test
    void aPerennialThatDoesNotStandTheWinterIsWarnedInOpenGroundAndShelteredInAPot() {
        long space = spaceOf(LOGIN);
        GardenSummary turin = gardens.saveGarden(space, LOGIN, null, "Turin", "Torino",
                new BigDecimal("45.07"), new BigDecimal("7.69"), "IT", 205);
        long ground = bed(space, turin, "Ground", PlotKind.GROUND, DirectSun.FULL_SUN);
        long pot = bed(space, turin, "Pot", PlotKind.POT, DirectSun.FULL_SUN);

        CropChecks inGround = proposals.checks(space, LOGIN, turin.id(), ground, "lemon", Month.NOVEMBER);
        assertThat(inGround.frost()).isNotNull();
        assertThat(inGround.frost().verdict()).isEqualTo(FrostSuitabilityRule.Verdict.NOT_IN_OPEN_GROUND);
        assertThat(proposals.checks(space, LOGIN, turin.id(), pot, "lemon", Month.NOVEMBER).frost().verdict())
                .isEqualTo(FrostSuitabilityRule.Verdict.SHELTER_IN_POT);
        assertThat(proposals.checks(space, LOGIN, turin.id(), ground, "tomato").frost())
                .as("an annual says nothing about the winter").isNull();
    }

    @Test
    void onlyAMemberOfTheSpaceReadsTheChecks() {
        long space = spaceOf(LOGIN);
        GardenSummary garden = romeChecksGarden(space);
        long area = bed(space, garden, "Bed", PlotKind.GROUND, DirectSun.FULL_SUN);

        assertThatThrownBy(() -> proposals.checks(space, "cp-ugo", garden.id(), area, "carrot"))
                .isInstanceOf(SpaceAccessException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> proposals.checks(space, LOGIN, 999_999, area,
                "carrot"));
        assertThatIllegalArgumentException().isThrownBy(() -> proposals.checks(space, LOGIN, garden.id(),
                999_999, "carrot"));
        assertThatIllegalArgumentException().isThrownBy(() -> proposals.checks(space, LOGIN, garden.id(), area,
                "no-such-crop"));
    }

    @Test
    void theFirstDayOfTheMonthIsItsNextOccurrence() {
        LocalDate november = LocalDate.of(2026, 11, 22);

        assertThat(CropProposalService.firstDayOf(Month.NOVEMBER, november)).isEqualTo(november);
        assertThat(CropProposalService.firstDayOf(Month.MARCH, november)).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(CropProposalService.firstDayOf(Month.DECEMBER, november)).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void aWindowWrapsOverTheNewYear() {
        assertThat(CropProposalService.covers(3, 5, 4)).isTrue();
        assertThat(CropProposalService.covers(3, 5, 6)).isFalse();
        assertThat(CropProposalService.covers(11, 2, 1)).isTrue();
        assertThat(CropProposalService.covers(11, 2, 3)).isFalse();
    }
}