package app.rootology.garden;

import app.rootology.TestcontainersConfiguration;
import app.rootology.facts.CropTraits;
import app.rootology.garden.CropProposalRule.Goal;
import app.rootology.garden.CropProposalRule.Proposal;
import app.rootology.garden.CropProposalRule.Uncertainty;
import app.rootology.garden.CropProposalService.AreaProposals;
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