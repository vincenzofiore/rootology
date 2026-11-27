package app.rootology.garden;

import app.rootology.facts.DepthClass;
import app.rootology.facts.Fact;
import app.rootology.garden.GardenViews.PlotSummary;

import java.util.Optional;

/**
 * container-depth-v1: a crop grows in a pot, a planter or a raised bed only when the soil there is at least as
 * deep as the crop's class asks ({@link DepthClass}). What it says is one of three things, and it never guesses:
 * ENOUGH when the declared depth reaches the class's minimum, TOO_SHALLOW when it does not, and UNKNOWN when
 * the area has no declared depth, when the area is not a container (open ground or under a greenhouse, where the
 * depth does not apply) or when the crop has no class in the curated file. No crop has a class today: the values
 * wait for the source's permission (#186), so every crop is UNKNOWN.
 */
public final class ContainerDepthRule {

    public static final String ID = "container-depth-v1";

    /** The source of a declared depth: the gardener, like the area's exposure (#54). */
    public static final String GARDEN_SOURCE = "garden";

    /** The least depth of a container area, and the most: whole centimetres (#186). */
    public static final int MIN_DEPTH_CM = 5;
    public static final int MAX_DEPTH_CM = 200;

    /** What the rule says about one crop in one area. */
    public enum Verdict {
        ENOUGH, TOO_SHALLOW, UNKNOWN
    }

    /**
     * The verdict for one crop in one area, with what it was read from: the class the crop needs (null when the
     * file gives none) and the depth the gardener declared (null when none). The planting dialog words the
     * warning from these, and unknown is shown smaller than a warning on purpose.
     */
    public record Check(Verdict verdict, DepthClass needed, Integer depthCm) {

        public static final Check UNKNOWN = new Check(Verdict.UNKNOWN, null, null);

        public Check {
            verdict = verdict == null ? Verdict.UNKNOWN : verdict;
        }
    }

    /** The kinds whose soil has a depth a crop must fit: a pot, a planter and a raised bed (#186). */
    public static boolean isContainer(PlotKind kind) {
        return kind == PlotKind.POT || kind == PlotKind.PLANTER || kind == PlotKind.RAISED_BED;
    }

    /**
     * The rule for one crop in one area: unknown when the area is not a container, when no depth is declared or
     * when the crop has no class; enough or too shallow only when both are known.
     */
    public static Check check(PlotKind kind, Integer depthCm, DepthClass crop) {
        if (!isContainer(kind) || depthCm == null || crop == null) {
            return new Check(Verdict.UNKNOWN, crop, depthCm);
        }
        return new Check(depthCm >= crop.minimumCm() ? Verdict.ENOUGH : Verdict.TOO_SHALLOW, crop, depthCm);
    }

    /**
     * The area's declared depth as a fact for the pipeline, when there is one: (area, hasSoilDepth, "45 cm"),
     * the same declaration the sun hours reach it by (#89), to be sent as a DECLARED reading with the `garden`
     * source. Nothing for open ground or a greenhouse, where a depth would mean nothing.
     */
    public static Optional<Fact> declared(PlotSummary area) {
        if (area == null || !isContainer(area.kind()) || area.depthCm() == null) {
            return Optional.empty();
        }
        return Optional.of(new Fact(area.name(), "hasSoilDepth", area.depthCm() + " cm"));
    }

    private ContainerDepthRule() {
    }
}
