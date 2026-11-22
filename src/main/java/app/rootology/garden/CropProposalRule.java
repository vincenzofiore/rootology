package app.rootology.garden;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Rule `crop-proposal-v1`: for a free area of a garden in a month, which crops to propose, ranked, with what
 * each proposal is based on.
 *
 * <p>A crop is a candidate for the area only when all of these hold: it has a window in the month with
 * activity SOW, TRANSPLANT or PLANT at the site (a seedbed, SOW_SEEDBED, is not sown in the area); the
 * rotation rule (`family-rotation-v1`) gives no warning for the area; light (`light-need-v1`) does not
 * exclude it (and when the area's direct sun or the crop's need is unknown, the crop stays a candidate with
 * the uncertainty LIGHT_UNKNOWN); and it is not an antagonist of a crop growing in the same area that day.
 *
 * <p>One or two goals rank the candidates, and their points are summed:
 * QUICK_HARVEST, for FOOD crops only, gives 1 - (cycleMinDays / longest cycleMinDays among the area's
 * candidates), and a FOOD crop without a cycle scores 0 with the uncertainty CYCLE_UNKNOWN;
 * VARIETY gives 1 when the crop's family grows nowhere in the garden that day;
 * BENEFICIALS gives 1 when the crop is the agent of a SERVICE_PLANT row of `pest-controls.csv`;
 * SOIL gives 1 when the crop's family is Fabaceae, the only family that fixes nitrogen in v1.
 *
 * <p>Order: score descending, then annual before perennial (a free area of the garden is filled for a season; a perennial takes it for years), then FOOD before SERVICE, then crop id; a crop scoring 0 is not proposed, and
 * at most three proposals are kept per area. A window the curated file did not verify adds the uncertainty
 * EDITORIAL_WINDOW, and BENEFICIALS met only through traditional rows adds TRADITIONAL_EVIDENCE.
 */
public final class CropProposalRule {

    public static final String ID = "crop-proposal-v1";

    /** At most this many crops are proposed for one area. */
    public static final int MAX_PER_AREA = 3;

    /** The only family that counts for SOIL in v1: legumes fix nitrogen. */
    public static final String NITROGEN_FIXERS = "Fabaceae";

    /** What to favour. One or two at a time. */
    public enum Goal {
        QUICK_HARVEST, VARIETY, BENEFICIALS, SOIL
    }

    /** What a proposal does not know, and the person should. */
    public enum Uncertainty {
        LIGHT_UNKNOWN, CYCLE_UNKNOWN, EDITORIAL_WINDOW, TRADITIONAL_EVIDENCE
    }

    /** A window of the month the crop has at the site, and where it comes from. */
    public record Window(String activity, int month, boolean derived, boolean verified, List<String> sources) {

        public Window {
            sources = List.copyOf(sources);
        }
    }

    /** What the rule knows about one crop at the site, for one area. */
    public record CropFacts(String cropId, String family, boolean service, Double cycleMinDays,
                            DirectSun lightNeed, List<Window> windows, boolean rotationWarning,
                            boolean antagonistInArea, boolean beneficial, boolean beneficialTraditionalOnly,
                            List<String> beneficialSources, boolean perennial) {

        public CropFacts {
            windows = List.copyOf(windows);
            beneficialSources = List.copyOf(beneficialSources);
        }
    }

    /** The area and the garden on the day of the proposal. */
    public record AreaFacts(long areaId, String areaName, DirectSun directSun, Set<String> familiesInGarden) {

        public AreaFacts {
            familiesInGarden = Set.copyOf(familiesInGarden);
        }
    }

    /** One crop proposed for one area. */
    public record Proposal(String cropId, double score, String activity, int month, boolean derived,
                           boolean windowVerified, Set<Goal> goals, List<Uncertainty> uncertainties,
                           List<String> sources) {

        public Proposal {
            goals = Set.copyOf(goals);
            uncertainties = List.copyOf(uncertainties);
            sources = List.copyOf(sources);
        }
    }

    private CropProposalRule() {
    }

    /**
     * The proposals for one free area: the candidates that pass the filters, scored by the goals, best first
     * and at most {@link #MAX_PER_AREA}. goals must hold one or two goals.
     */
    public static List<Proposal> propose(AreaFacts area, List<CropFacts> crops, List<Goal> goals) {
        checkGoals(goals);
        EnumSet<Goal> chosen = EnumSet.copyOf(goals);
        List<CropFacts> candidates = crops.stream().filter(crop -> candidate(crop, area)).toList();
        double longest = candidates.stream().map(CropFacts::cycleMinDays).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).max().orElse(0);
        List<Ranked> ranked = new ArrayList<>();
        for (CropFacts crop : candidates) {
            Ranked result = rank(crop, area, chosen, longest);
            if (result != null) {
                ranked.add(result);
            }
        }
        ranked.sort(Ranked.ORDER);
        return ranked.stream().limit(MAX_PER_AREA).map(Ranked::proposal).toList();
    }

    /** Goals must be one or two, and no other number is a question this rule can answer. */
    public static void checkGoals(List<Goal> goals) {
        if (goals == null || goals.isEmpty() || goals.size() > 2 || goals.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A proposal needs one or two goals");
        }
    }

    private static boolean candidate(CropFacts crop, AreaFacts area) {
        if (crop.windows().isEmpty() || crop.rotationWarning() || crop.antagonistInArea()) {
            return false;
        }
        return area.directSun() == null || crop.lightNeed() == null
                || !LightRule.tooDark(area.directSun(), crop.lightNeed());
    }

    /** A crop with its proposal, and whether it is a food crop, for the tie order. */
    private record Ranked(CropFacts crop, Proposal proposal) {

        static final Comparator<Ranked> ORDER = Comparator
                .comparingDouble((Ranked r) -> -r.proposal().score())
                .thenComparing(r -> r.crop().perennial())
                .thenComparing(r -> r.crop().service())
                .thenComparing(r -> r.proposal().cropId());
    }

    private static Ranked rank(CropFacts crop, AreaFacts area, Set<Goal> goals, double longest) {
        EnumSet<Goal> met = EnumSet.noneOf(Goal.class);
        EnumSet<Uncertainty> uncertainties = EnumSet.noneOf(Uncertainty.class);
        if (area.directSun() == null || crop.lightNeed() == null) {
            uncertainties.add(Uncertainty.LIGHT_UNKNOWN);
        }
        double score = 0;
        for (Goal goal : goals) {
            double points = points(goal, crop, area, longest, uncertainties);
            score += points;
            if (points > 0) {
                met.add(goal);
            }
        }
        if (score <= 0) {
            return null;
        }
        Window window = crop.windows().getFirst();
        if (!window.verified()) {
            uncertainties.add(Uncertainty.EDITORIAL_WINDOW);
        }
        if (met.contains(Goal.BENEFICIALS) && crop.beneficialTraditionalOnly()) {
            uncertainties.add(Uncertainty.TRADITIONAL_EVIDENCE);
        }
        return new Ranked(crop, new Proposal(crop.cropId(), score, window.activity(), window.month(),
                window.derived(), window.verified(), met, List.copyOf(uncertainties),
                sources(crop, window, goals)));
    }

    private static double points(Goal goal, CropFacts crop, AreaFacts area, double longest,
                                 Set<Uncertainty> uncertainties) {
        return switch (goal) {
            case QUICK_HARVEST -> quickHarvest(crop, longest, uncertainties);
            case VARIETY -> area.familiesInGarden().contains(crop.family()) ? 0 : 1;
            case BENEFICIALS -> crop.beneficial() ? 1 : 0;
            case SOIL -> NITROGEN_FIXERS.equals(crop.family()) ? 1 : 0;
        };
    }

    /** The shorter the cycle the better, up to the longest cycle among the area's candidates. SERVICE is not food. */
    private static double quickHarvest(CropFacts crop, double longest, Set<Uncertainty> uncertainties) {
        if (crop.service()) {
            return 0;
        }
        if (crop.cycleMinDays() == null || longest <= 0) {
            uncertainties.add(Uncertainty.CYCLE_UNKNOWN);
            return 0;
        }
        return 1 - crop.cycleMinDays() / longest;
    }

    /** The window's sources, plus the source of every value a goal used. */
    private static List<String> sources(CropFacts crop, Window window, Set<Goal> goals) {
        Set<String> sources = new LinkedHashSet<>(window.sources());
        if (goals.contains(Goal.VARIETY) || goals.contains(Goal.SOIL)) {
            sources.add("wikidata");
        }
        if (crop.lightNeed() != null
                || (goals.contains(Goal.QUICK_HARVEST) && !crop.service() && crop.cycleMinDays() != null)) {
            sources.add("fao-ecocrop");
        }
        if (goals.contains(Goal.BENEFICIALS) && crop.beneficial()) {
            sources.addAll(crop.beneficialSources());
        }
        return List.copyOf(sources);
    }
}