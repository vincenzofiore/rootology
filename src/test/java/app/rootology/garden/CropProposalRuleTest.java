package app.rootology.garden;

import app.rootology.garden.CropProposalRule.AreaFacts;
import app.rootology.garden.CropProposalRule.CropFacts;
import app.rootology.garden.CropProposalRule.Goal;
import app.rootology.garden.CropProposalRule.Proposal;
import app.rootology.garden.CropProposalRule.Uncertainty;
import app.rootology.garden.CropProposalRule.Window;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/** The pure rule `crop-proposal-v1`: its filters, the goals' points, the order and the limit. */
class CropProposalRuleTest {

    private static final AreaFacts SUNNY = new AreaFacts(1, "Sunny", DirectSun.FULL_SUN, Set.of());

    private static Window window() {
        return new Window("SOW", 3, true, true, List.of("rootology-editorial", "agera5"));
    }

    /** A food crop of a family with a cycle, a light need, a verified window and nothing else. */
    private static CropFacts crop(String id, String family) {
        return crop(id, family, false, 60.0, DirectSun.PART_SHADE, List.of(window()), false, false,
                false, false, List.of());
    }

    private static CropFacts crop(String id, String family, boolean service, Double cycle, DirectSun lightNeed,
                                  List<Window> windows, boolean rotationWarning, boolean antagonistInArea,
                                  boolean beneficial, boolean traditionalOnly, List<String> beneficialSources) {
        return new CropFacts(id, family, service, cycle, lightNeed, windows, rotationWarning, antagonistInArea,
                beneficial, traditionalOnly, beneficialSources, false);
    }

    private static AreaFacts area(DirectSun sun) {
        return new AreaFacts(1, "Area", sun, Set.of());
    }

    @Test
    void aCropWithoutAWindowInTheAreaIsNotACandidate() {
        List<Proposal> proposals = CropProposalRule.propose(SUNNY,
                List.of(crop("basil", "Lamiaceae", false, 60.0, DirectSun.PART_SHADE, List.of(), false, false,
                        false, false, List.of())), List.of(Goal.VARIETY));

        assertThat(proposals).isEmpty();
    }

    @Test
    void aRotationWarningRemovesTheCrop() {
        List<Proposal> proposals = CropProposalRule.propose(SUNNY, List.of(
                crop("basil", "Lamiaceae", false, 60.0, null, List.of(window()), true, false, false, false,
                        List.of()),
                crop("borage", "Boraginaceae")), List.of(Goal.VARIETY));

        assertThat(proposals).extracting(Proposal::cropId).containsExactly("borage");
    }

    @Test
    void anAntagonistGrowingInTheAreaRemovesTheCrop() {
        List<Proposal> proposals = CropProposalRule.propose(SUNNY, List.of(
                crop("tomato", "Solanaceae", false, 60.0, null, List.of(window()), false, true, false, false,
                        List.of()),
                crop("borage", "Boraginaceae")), List.of(Goal.VARIETY));

        assertThat(proposals).extracting(Proposal::cropId).containsExactly("borage");
    }

    @Test
    void aCropNeedingMoreSunThanTheAreaGetsIsRemoved() {
        List<Proposal> proposals = CropProposalRule.propose(area(DirectSun.PART_SHADE), List.of(
                crop("basil", "Lamiaceae", false, 60.0, DirectSun.FULL_SUN, List.of(window()), false, false,
                        false, false, List.of()),
                crop("carrot", "Apiaceae")), List.of(Goal.VARIETY));

        assertThat(proposals).extracting(Proposal::cropId).containsExactly("carrot");
    }

    @Test
    void anUnknownLightKeepsTheCropAndSaysSo() {
        CropFacts unknownNeed = crop("basil", "Lamiaceae", false, 60.0, null, List.of(window()), false, false,
                false, false, List.of());

        List<Proposal> unknownArea = CropProposalRule.propose(area(null), List.of(unknownNeed),
                List.of(Goal.VARIETY));
        List<Proposal> unknownCrop = CropProposalRule.propose(SUNNY, List.of(unknownNeed), List.of(Goal.VARIETY));

        assertThat(unknownArea).singleElement().satisfies(p ->
                assertThat(p.uncertainties()).contains(Uncertainty.LIGHT_UNKNOWN));
        assertThat(unknownCrop).singleElement().satisfies(p ->
                assertThat(p.uncertainties()).contains(Uncertainty.LIGHT_UNKNOWN));
    }

    @Test
    void quickHarvestScoresTheCycleAgainstTheLongestOfTheAreasCandidates() {
        List<Proposal> proposals = CropProposalRule.propose(SUNNY, List.of(
                crop("quick", "Brassicaceae", false, 30.0, null, List.of(window()), false, false, false, false,
                        List.of()),
                crop("slow", "Apiaceae", false, 90.0, null, List.of(window()), false, false, false, false,
                        List.of())), List.of(Goal.QUICK_HARVEST));

        assertThat(proposals).singleElement().satisfies(p -> {
            assertThat(p.cropId()).isEqualTo("quick");
            assertThat(p.score()).isCloseTo(2.0 / 3, within(0.0001));
            assertThat(p.goals()).containsExactly(Goal.QUICK_HARVEST);
        });
    }

    @Test
    void quickHarvestNeverScoresAServiceCropAndNamesAMissingCycle() {
        CropFacts service = crop("phacelia", "Boraginaceae", true, 30.0, null, List.of(window()), false, false,
                true, false, List.of("rootology-editorial"));
        CropFacts noCycle = crop("basil", "Lamiaceae", false, null, null, List.of(window()), false, false,
                false, false, List.of());

        List<Proposal> proposals = CropProposalRule.propose(
                new AreaFacts(1, "Area", DirectSun.FULL_SUN, Set.of("Boraginaceae")), List.of(service, noCycle),
                List.of(Goal.QUICK_HARVEST, Goal.VARIETY));

        assertThat(proposals).singleElement().satisfies(p -> {
            assertThat(p.cropId()).isEqualTo("basil");
            assertThat(p.score()).isEqualTo(1);
            assertThat(p.uncertainties()).contains(Uncertainty.CYCLE_UNKNOWN);
        });
    }

    @Test
    void varietyScoresAFamilyThatGrowsNowhereInTheGarden() {
        AreaFacts garden = new AreaFacts(1, "Area", DirectSun.FULL_SUN, Set.of("Fabaceae"));

        List<Proposal> proposals = CropProposalRule.propose(garden, List.of(
                crop("pea", "Fabaceae"), crop("basil", "Lamiaceae")), List.of(Goal.VARIETY));

        assertThat(proposals).extracting(Proposal::cropId).containsExactly("basil");
        assertThat(proposals.getFirst().sources()).contains("wikidata");
    }

    @Test
    void beneficialsScoresTheAgentOfAServicePlantRowAndKeepsItsSources() {
        CropFacts sweetAlyssum = crop("sweet-alyssum", "Brassicaceae", true, null, null, List.of(window()),
                false, false, true, false, List.of("ucipm-diamondback-moth", "rootology-editorial"));

        List<Proposal> proposals = CropProposalRule.propose(SUNNY, List.of(sweetAlyssum, crop("basil",
                "Lamiaceae")), List.of(Goal.BENEFICIALS));

        assertThat(proposals).singleElement().satisfies(p -> {
            assertThat(p.cropId()).isEqualTo("sweet-alyssum");
            assertThat(p.goals()).containsExactly(Goal.BENEFICIALS);
            assertThat(p.sources()).contains("ucipm-diamondback-moth", "rootology-editorial");
            assertThat(p.uncertainties()).doesNotContain(Uncertainty.TRADITIONAL_EVIDENCE);
        });
    }

    @Test
    void beneficialsMetOnlyThroughTraditionalRowsSaysSo() {
        CropFacts whiteMustard = crop("white-mustard", "Brassicaceae", true, null, null, List.of(window()),
                false, false, true, true, List.of("rootology-editorial"));

        List<Proposal> proposals = CropProposalRule.propose(SUNNY, List.of(whiteMustard),
                List.of(Goal.BENEFICIALS));

        assertThat(proposals).singleElement().satisfies(p ->
                assertThat(p.uncertainties()).contains(Uncertainty.TRADITIONAL_EVIDENCE));
    }

    @Test
    void soilScoresOnlyTheLegumes() {
        List<Proposal> proposals = CropProposalRule.propose(SUNNY, List.of(
                crop("pea", "Fabaceae"), crop("basil", "Lamiaceae")), List.of(Goal.SOIL));

        assertThat(proposals).singleElement().satisfies(p -> {
            assertThat(p.cropId()).isEqualTo("pea");
            assertThat(p.score()).isEqualTo(1);
        });
    }

    @Test
    void twoGoalsSumTheirPoints() {
        AreaFacts garden = new AreaFacts(1, "Area", DirectSun.FULL_SUN, Set.of("Solanaceae"));

        List<Proposal> proposals = CropProposalRule.propose(garden, List.of(
                crop("pea", "Fabaceae"), crop("basil", "Lamiaceae")), List.of(Goal.VARIETY, Goal.SOIL));

        assertThat(proposals).extracting(Proposal::cropId).containsExactly("pea", "basil");
        assertThat(proposals).extracting(Proposal::score).containsExactly(2.0, 1.0);
        assertThat(proposals.getFirst().goals()).containsExactlyInAnyOrder(Goal.VARIETY, Goal.SOIL);
    }

    @Test
    void tiesPutFoodBeforeServiceThenTheCropIdAndKeepOnlyThree() {
        List<Proposal> proposals = CropProposalRule.propose(SUNNY, List.of(
                crop("sweet-alyssum", "Brassicaceae", true, null, null, List.of(window()), false, false,
                        false, false, List.of()),
                crop("chervil", "Apiaceae"), crop("borage", "Boraginaceae"), crop("basil", "Lamiaceae")),
                List.of(Goal.VARIETY));

        assertThat(proposals).extracting(Proposal::cropId)
                .containsExactly("basil", "borage", "chervil");
    }

    @Test
    void anAnnualComesBeforeAPerennialOfTheSameScore() {
        CropFacts aronia = new CropFacts("aronia", "Rosaceae", false, 60.0, DirectSun.PART_SHADE, List.of(window()),
                false, false, false, false, List.of(), true);
        List<Proposal> proposals = CropProposalRule.propose(SUNNY, List.of(aronia, crop("zucchini", "Cucurbitaceae")),
                List.of(Goal.VARIETY));

        assertThat(proposals).extracting(Proposal::cropId).containsExactly("zucchini", "aronia");
    }

    @Test
    void aCropThatScoresNothingIsNotProposed() {
        List<Proposal> proposals = CropProposalRule.propose(SUNNY, List.of(
                crop("pea", "Fabaceae")), List.of(Goal.BENEFICIALS));

        assertThat(proposals).isEmpty();
    }

    @Test
    void theProposalCarriesTheWindowAndFlagsAnUnverifiedOne() {
        Window editorial = new Window("PLANT", 3, false, false, List.of("rootology-editorial"));
        Window derived = new Window("SOW", 3, true, true, List.of("agera5"));
        CropFacts crop = crop("basil", "Lamiaceae", false, 60.0, null, List.of(derived, editorial), false,
                false, false, false, List.of());

        Proposal proposal = CropProposalRule.propose(SUNNY, List.of(crop), List.of(Goal.VARIETY)).getFirst();

        assertThat(proposal.activity()).isEqualTo("SOW");
        assertThat(proposal.month()).isEqualTo(3);
        assertThat(proposal.derived()).isTrue();
        assertThat(proposal.windowVerified()).isTrue();
        assertThat(proposal.uncertainties()).doesNotContain(Uncertainty.EDITORIAL_WINDOW);
        assertThat(proposal.sources()).contains("agera5", "wikidata");
    }

    @Test
    void anUnverifiedWindowIsEditorial() {
        Window editorial = new Window("SOW", 3, false, false, List.of("rootology-editorial"));

        Proposal proposal = CropProposalRule.propose(SUNNY, List.of(crop("basil", "Lamiaceae", false, 60.0,
                null, List.of(editorial), false, false, false, false, List.of())), List.of(Goal.VARIETY))
                .getFirst();

        assertThat(proposal.windowVerified()).isFalse();
        assertThat(proposal.uncertainties()).contains(Uncertainty.EDITORIAL_WINDOW);
    }

    @Test
    void oneOrTwoGoalsOnly() {
        List<CropFacts> crops = List.of(crop("basil", "Lamiaceae"));

        assertThatIllegalArgumentException().isThrownBy(() -> CropProposalRule.propose(SUNNY, crops, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> CropProposalRule.propose(SUNNY, crops,
                List.of(Goal.VARIETY, Goal.SOIL, Goal.BENEFICIALS)));
        assertThatIllegalArgumentException().isThrownBy(() -> CropProposalRule.propose(SUNNY, crops, null));
        assertThatIllegalArgumentException().isThrownBy(() -> CropProposalRule.propose(SUNNY, crops,
                java.util.Arrays.asList(Goal.VARIETY, null)));
        assertThat(CropProposalRule.propose(SUNNY, crops, List.of(Goal.VARIETY))).isNotEmpty();
    }
}