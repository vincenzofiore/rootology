package app.rootology.facts;

import java.util.Optional;

/**
 * Rule `frost-suitability-v1` (#147): will a perennial survive the winter at a site?
 *
 * <p>Only for a crop that is {@link Crop#perennial()} with a known {@link Crop#frostHardiness()}, at a site
 * with a known winter minimum (the lowest daily minimum reached or passed in one winter in five). When the
 * crop stands less cold than the site's winter minimum (its hardiness is above it), the verdict depends on
 * where it grows: in a pot or a planter it can be moved, so it is sheltered in winter; in a greenhouse the
 * greenhouse already protects it and this rule says nothing; on open ground (a plot in the ground or a raised
 * bed, or no area at all) it does not survive the winter there. A crop that stands the site's winter says
 * nothing: there is nothing to warn about.
 */
public final class FrostSuitabilityRule {

    public static final String ID = "frost-suitability-v1";

    /** What the rule says about one crop at one site. */
    public enum Verdict {
        /** The crop stands the site's 1-in-5 winter minimum: no note at all. */
        SURVIVES,
        /** It does not stand it, and it grows in open ground: it does not survive the winter there. */
        NOT_IN_OPEN_GROUND,
        /** It does not stand it, but it grows in a pot or a planter: shelter it in winter. */
        SHELTER_IN_POT
    }

    /**
     * The verdict with the two numbers the texts cite: the crop's hardiness and the site's winter minimum, °C.
     */
    public record Result(Verdict verdict, double hardiness, double winterMinimum) {

        public boolean survives() {
            return verdict == Verdict.SURVIVES;
        }
    }

    /**
     * What the rule says, or empty when it says nothing: an annual, a perennial whose hardiness is not known,
     * a site without a winter minimum, or a greenhouse (which protects the crop already).
     *
     * @param areaKind the area's kind as {@code garden.PlotKind} names it, or null when no area is involved
     */
    public static Optional<Result> of(Crop crop, SiteProfile site, String areaKind) {
        if (crop == null || !crop.perennial() || crop.frostHardiness() == null
                || site == null || site.winterMinimum() == null) {
            return Optional.empty();
        }
        double hardiness = crop.frostHardiness();
        double winterMinimum = site.winterMinimum();
        if (hardiness <= winterMinimum) {
            return Optional.of(new Result(Verdict.SURVIVES, hardiness, winterMinimum));
        }
        if ("GREENHOUSE".equals(areaKind)) {
            // The greenhouse already protects it: the rule does not guess what it does to this crop.
            return Optional.empty();
        }
        boolean pot = "POT".equals(areaKind) || "PLANTER".equals(areaKind);
        return Optional.of(new Result(pot ? Verdict.SHELTER_IN_POT : Verdict.NOT_IN_OPEN_GROUND,
                hardiness, winterMinimum));
    }

    private FrostSuitabilityRule() {
    }
}
