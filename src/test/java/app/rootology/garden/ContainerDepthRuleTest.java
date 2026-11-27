package app.rootology.garden;

import app.rootology.facts.DepthClass;
import app.rootology.facts.Fact;
import app.rootology.garden.ContainerDepthRule.Verdict;
import app.rootology.garden.GardenViews.PlotSummary;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * container-depth-v1 (#186): a container is deep enough when its declared depth reaches the crop's class, too
 * shallow when it does not, and unknown — never guessed — without a declared depth, outside a container, or
 * when the curated file gives the crop no class (which, today, is every crop: the values wait for their source).
 */
class ContainerDepthRuleTest {

    @Test
    void aDeepEnoughContainerIsEnoughAndTheClassMinimumIsInclusive() {
        assertThat(ContainerDepthRule.check(PlotKind.POT, 30, DepthClass.DEEP).verdict()).isEqualTo(Verdict.ENOUGH);
        assertThat(ContainerDepthRule.check(PlotKind.PLANTER, 40, DepthClass.SHALLOW).verdict())
                .isEqualTo(Verdict.ENOUGH);
        assertThat(ContainerDepthRule.check(PlotKind.RAISED_BED, 25, DepthClass.MEDIUM).verdict())
                .as("the minimum itself is enough").isEqualTo(Verdict.ENOUGH);
        assertThat(ContainerDepthRule.check(PlotKind.POT, 200, DepthClass.DEEP).verdict()).isEqualTo(Verdict.ENOUGH);
    }

    @Test
    void aContainerShallowerThanTheClassIsTooShallow() {
        ContainerDepthRule.Check shallow = ContainerDepthRule.check(PlotKind.POT, 14, DepthClass.SHALLOW);
        assertThat(shallow.verdict()).isEqualTo(Verdict.TOO_SHALLOW);
        assertThat(shallow.needed()).as("what the crop asks for").isEqualTo(DepthClass.SHALLOW);
        assertThat(shallow.depthCm()).as("what the gardener declared").isEqualTo(14);
        assertThat(ContainerDepthRule.check(PlotKind.POT, 24, DepthClass.MEDIUM).verdict())
                .isEqualTo(Verdict.TOO_SHALLOW);
        assertThat(ContainerDepthRule.check(PlotKind.RAISED_BED, 29, DepthClass.DEEP).verdict())
                .isEqualTo(Verdict.TOO_SHALLOW);
    }

    @Test
    void withoutADeclaredDepthItSaysNothing() {
        ContainerDepthRule.Check none = ContainerDepthRule.check(PlotKind.POT, null, DepthClass.SHALLOW);
        assertThat(none.verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(none.needed()).isEqualTo(DepthClass.SHALLOW);
        assertThat(none.depthCm()).isNull();
    }

    @Test
    void openGroundAndAGreenhouseHaveNoDepthAndSoNoVerdict() {
        assertThat(ContainerDepthRule.check(PlotKind.GROUND, 30, DepthClass.SHALLOW).verdict())
                .isEqualTo(Verdict.UNKNOWN);
        assertThat(ContainerDepthRule.check(PlotKind.GREENHOUSE, 30, DepthClass.SHALLOW).verdict())
                .isEqualTo(Verdict.UNKNOWN);
        assertThat(ContainerDepthRule.isContainer(PlotKind.GROUND)).isFalse();
        assertThat(ContainerDepthRule.isContainer(PlotKind.GREENHOUSE)).isFalse();
        assertThat(ContainerDepthRule.isContainer(PlotKind.POT)).isTrue();
        assertThat(ContainerDepthRule.isContainer(PlotKind.PLANTER)).isTrue();
        assertThat(ContainerDepthRule.isContainer(PlotKind.RAISED_BED)).isTrue();
    }

    @Test
    void aCropWithoutAClassIsUnknownEvenInADeepContainer() {
        ContainerDepthRule.Check none = ContainerDepthRule.check(PlotKind.POT, 40, null);
        assertThat(none.verdict()).isEqualTo(Verdict.UNKNOWN);
        assertThat(none.needed()).isNull();
        assertThat(none.depthCm()).as("the declared depth is still reported").isEqualTo(40);
    }

    @Test
    void theClassesCarryTheirMinimumInCentimetres() {
        assertThat(DepthClass.SHALLOW.minimumCm()).isEqualTo(15);
        assertThat(DepthClass.MEDIUM.minimumCm()).isEqualTo(25);
        assertThat(DepthClass.DEEP.minimumCm()).isEqualTo(30);
        assertThat(DepthClass.names()).containsExactlyInAnyOrder("SHALLOW", "MEDIUM", "DEEP");
        assertThat(DepthClass.parse(" deep ")).contains(DepthClass.DEEP);
        assertThat(DepthClass.parse("tall")).isEmpty();
        assertThat(DepthClass.parse(null)).isEmpty();
    }

    @Test
    void theDeclaredDepthIsAFactForThePipelineOnlyInAContainer() {
        PlotSummary pot = area(PlotKind.POT, 45);
        assertThat(ContainerDepthRule.declared(pot)).as("the same declaration the sun hours reach the pipeline by")
                .contains(new Fact("Vaso", "hasSoilDepth", "45 cm"));
        assertThat(ContainerDepthRule.declared(area(PlotKind.GROUND, 45))).isEmpty();
        assertThat(ContainerDepthRule.declared(area(PlotKind.GREENHOUSE, 45))).isEmpty();
        assertThat(ContainerDepthRule.declared(area(PlotKind.POT, null))).isEmpty();
        assertThat(ContainerDepthRule.declared(null)).isEmpty();
    }

    private static PlotSummary area(PlotKind kind, Integer depthCm) {
        return new PlotSummary(1, 1, "Vaso", kind, Soil.POTTING_MIX, null, null, null, null, null,
                null, null, null, null, depthCm);
    }
}
