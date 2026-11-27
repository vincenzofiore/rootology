package app.rootology.garden;

import app.rootology.facts.ClimateWindows;
import app.rootology.facts.ClimateWindows.ClimateWindow;
import app.rootology.facts.Crop;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.CropTraits;
import app.rootology.facts.FrostSuitabilityRule;
import app.rootology.facts.SiteProfile;
import app.rootology.garden.CropProposalRule.AreaFacts;
import app.rootology.garden.CropProposalRule.CropFacts;
import app.rootology.garden.CropProposalRule.Goal;
import app.rootology.garden.CropProposalRule.Proposal;
import app.rootology.garden.CropProposalRule.Window;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.GardenViews.PlotSummary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The data behind `crop-proposal-v1` (#141): it reads a garden's free areas, the windows of the site the
 * pipeline uses for `SOW_IN_PLOT` (a garden without a profile gets the pipeline's default, and the result
 * says so), the rotation warnings and the crops growing in the area, and leaves the choice and the ranking
 * to {@link CropProposalRule}. Nothing here calls Jev or Claude, and nothing is written.
 *
 * <p>A free area is a plot with nothing growing on the first day of the month, as {@link GardenService}
 * reads plantings. Access follows the garden: only a member of the space reads the proposal, and a garden of
 * another space is unknown.
 *
 * <p>Since #185 the per-crop, per-area facts are one method, {@link #checks}, which the planting dialog reads
 * for the crop a person chooses: the proposals use the same computation, so the two can never disagree.
 */
@Component
public class CropProposalService {

    /** The proposals of one area, in rank order. */
    public record AreaProposals(long areaId, String areaName, List<Proposal> proposals) {

        public AreaProposals {
            proposals = List.copyOf(proposals);
        }
    }

    /** The proposal for one garden in one month. siteFromGarden is false when the default site was used. */
    public record Proposals(long gardenId, String gardenName, Month month, boolean siteFromGarden,
                            List<AreaProposals> areas) {

        public Proposals {
            areas = List.copyOf(areas);
        }
    }

    /** The light verdict of `light-need-v1` for one crop in one area (#185): unknown until both values are known. */
    public enum Light {
        ENOUGH, TOO_DARK, UNKNOWN
    }

    /**
     * What the garden's rules say about one crop in one area (#185): the facts `crop-proposal-v1` judges, plus
     * the names of the companions and antagonists growing there and the rotation warning itself, so the
     * planting dialog and the proposals can never disagree.
     *
     * <p>rotation is the `family-rotation-v1` warning, or null when the rule says nothing; frost is what
     * `frost-suitability-v1` says, or null when it says nothing; inWindow is false when the crop has no
     * SOW, TRANSPLANT or PLANT window at the site this month.
     */
    public record CropChecks(CropFacts facts, List<String> companionsInArea, List<String> antagonistsInArea,
                             RotationRule.RotationWarning rotation, Light light) {

        public CropChecks {
            companionsInArea = List.copyOf(companionsInArea);
            antagonistsInArea = List.copyOf(antagonistsInArea);
        }

        public String cropId() {
            return facts.cropId();
        }

        public String family() {
            return facts.family();
        }

        /** A service plant or green manure, not food (#140). */
        public boolean service() {
            return facts.service();
        }

        /** A window of the month that puts the crop in the area; false is "outside its window". */
        public boolean inWindow() {
            return !facts.windows().isEmpty();
        }

        public FrostSuitabilityRule.Result frost() {
            return facts.frost();
        }
    }

    /** The activities of a window that put a crop in the area itself, best first; a seedbed does not. */
    private static final List<String> IN_THE_AREA = List.of("SOW", "TRANSPLANT", "PLANT");

    private final GardenService gardens;
    private final GardenClimate sites;
    private final ClimateWindows windows;
    private final CropCatalog crops;
    private final CropTraits traits;
    private final RotationRule rotation;
    private final Clock clock;

    CropProposalService(GardenService gardens, GardenClimate sites, ClimateWindows windows, CropCatalog crops,
                        CropTraits traits, RotationRule rotation, Clock clock) {
        this.gardens = gardens;
        this.sites = sites;
        this.windows = windows;
        this.crops = crops;
        this.traits = traits;
        this.rotation = rotation;
        this.clock = clock;
    }

    /** The proposals for the garden's current month (#158). */
    @Transactional(readOnly = true)
    public Proposals propose(long spaceId, String login, long gardenId, List<Goal> goals) {
        return propose(spaceId, login, gardenId, today(garden(spaceId, login, gardenId)).getMonth(), goals);
    }

    /** The proposals for a month: at most three crops for each free area of the garden. */
    @Transactional(readOnly = true)
    public Proposals propose(long spaceId, String login, long gardenId, Month month, List<Goal> goals) {
        CropProposalRule.checkGoals(goals);
        GardenSummary garden = garden(spaceId, login, gardenId);
        Optional<SiteProfile> own = sites.profile(garden);
        SiteProfile site = own.orElseGet(sites::defaultProfile);
        LocalDate day = firstDayOf(month, today(garden));
        List<PlantingSummary> growing = gardens.growingOn(spaceId, login, gardenId, day);
        Set<Long> busy = growing.stream().map(PlantingSummary::plotId).collect(Collectors.toSet());
        Set<String> families = families(growing);
        Map<String, List<Window>> windowsOfMonth = windowsOfMonth(site, month);
        List<AreaProposals> areas = new ArrayList<>();
        for (PlotSummary plot : gardens.plots(spaceId, login, gardenId)) {
            if (busy.contains(plot.id())) {
                continue;
            }
            List<PlantingSummary> inArea = inArea(growing, plot);
            List<PlantingSummary> history = gardens.plantings(spaceId, login, plot.id());
            List<CropFacts> candidates = windowsOfMonth.entrySet().stream()
                    .map(entry -> checksOf(entry.getKey(), entry.getValue(), inArea, history, day, site, plot, month))
                    .map(CropChecks::facts).toList();
            areas.add(new AreaProposals(plot.id(), plot.name(), CropProposalRule.propose(
                    new AreaFacts(plot.id(), plot.name(), plot.directSunIn(month), families), candidates, goals)));
        }
        return new Proposals(garden.id(), garden.name(), month, own.isPresent(), areas);
    }

    /** The checks for the garden's current month (#158), as the planting dialog shows them (#185). */
    @Transactional(readOnly = true)
    public CropChecks checks(long spaceId, String login, long gardenId, long areaId, String cropId) {
        return checks(spaceId, login, gardenId, areaId, cropId, today(garden(spaceId, login, gardenId)).getMonth());
    }

    /**
     * What the rules say about one crop in one area in a month (#185). The same facts `propose` builds, so a
     * crop the dialog warns about is the crop the proposals leave out. The access checks are the proposals':
     * only a member of the space reads the garden, and a garden or area of another space is unknown.
     */
    @Transactional(readOnly = true)
    public CropChecks checks(long spaceId, String login, long gardenId, long areaId, String cropId, Month month) {
        crops.byId(cropId).orElseThrow(() -> new IllegalArgumentException("Unknown crop: " + cropId));
        GardenSummary garden = garden(spaceId, login, gardenId);
        SiteProfile site = sites.profile(garden).orElseGet(sites::defaultProfile);
        LocalDate day = firstDayOf(month, today(garden));
        PlotSummary plot = gardens.plots(spaceId, login, gardenId).stream()
                .filter(p -> p.id() == areaId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such area in this garden"));
        List<PlantingSummary> inArea = inArea(gardens.growingOn(spaceId, login, gardenId, day), plot);
        List<PlantingSummary> history = gardens.plantings(spaceId, login, plot.id());
        List<Window> cropWindows = windowsOfMonth(site, month).getOrDefault(cropId, List.of());
        return checksOf(cropId, cropWindows, inArea, history, day, site, plot, month);
    }

    /**
     * Every window of the month that puts a crop in the area, by crop, the preferred activity first: the
     * first window is the one the proposal reports.
     */
    private Map<String, List<Window>> windowsOfMonth(SiteProfile site, Month month) {
        Map<String, List<Window>> byCrop = new HashMap<>();
        for (ClimateWindow source : windows.windows(site)) {
            if (IN_THE_AREA.contains(source.window().activity())
                    && covers(source.window().fromMonth(), source.window().toMonth(), month.getValue())) {
                byCrop.computeIfAbsent(source.window().cropId(), cropId -> new ArrayList<>())
                        .add(new Window(source.window().activity(), month.getValue(), source.derived(),
                                source.window().verified(), ClimateWindows.sources(source, site)));
            }
        }
        byCrop.values().forEach(list -> list.sort(
                Comparator.comparingInt(window -> IN_THE_AREA.indexOf(window.activity()))));
        return byCrop;
    }

    /**
     * The facts and the checks for one crop in one area: the rotation warning, the companions and antagonists
     * growing there, the light verdict and frost suitability, all from the rules the proposals use (#185).
     */
    private CropChecks checksOf(String cropId, List<Window> windows, List<PlantingSummary> inArea,
                                List<PlantingSummary> history, LocalDate day, SiteProfile site, PlotSummary plot,
                                Month month) {
        Crop crop = crops.byId(cropId).orElseThrow(() -> new IllegalStateException("Unknown crop: " + cropId));
        Optional<RotationRule.RotationWarning> rotationWarning = rotation.check(history, cropId, day);
        Set<String> companions = traits.companions(cropId);
        List<String> companionsInArea = inArea.stream().map(PlantingSummary::cropId)
                // a person planting reads a pair both ways; the pipeline keeps the declared direction
                .filter(other -> !other.equals(cropId)
                        && (companions.contains(other) || traits.companions(other).contains(cropId)))
                .distinct().sorted().toList();
        List<String> antagonistsInArea = inArea.stream().map(PlantingSummary::cropId)
                .filter(other -> !other.equals(cropId) && traits.antagonists(cropId, other))
                .distinct().sorted().toList();
        DirectSun sun = plot.directSunIn(month);
        Optional<DirectSun> lightNeed = traits.lightOptimum(cropId).flatMap(LightRule::needs);
        Light light = sun == null || lightNeed.isEmpty() ? Light.UNKNOWN
                : LightRule.tooDark(sun, lightNeed.get()) ? Light.TOO_DARK : Light.ENOUGH;
        return new CropChecks(cropFacts(crop, windows, rotationWarning.isPresent(), !antagonistsInArea.isEmpty(),
                site, plot), companionsInArea, antagonistsInArea, rotationWarning.orElse(null), light);
    }

    private CropFacts cropFacts(Crop crop, List<Window> windows, boolean rotationWarning, boolean antagonistInArea,
                                SiteProfile site, PlotSummary plot) {
        String cropId = crop.id();
        Optional<CropTraits.Beneficial> beneficial = traits.beneficial(cropId);
        return new CropFacts(cropId, crop.family(), crop.service(),
                traits.cycleMinDays(cropId).orElse(null),
                traits.lightOptimum(cropId).flatMap(LightRule::needs).orElse(null),
                windows, rotationWarning, antagonistInArea,
                beneficial.isPresent(), beneficial.map(CropTraits.Beneficial::traditionalOnly).orElse(false),
                beneficial.map(CropTraits.Beneficial::sources).orElse(List.of()), crop.perennial(),
                // A perennial that does not stand the area's winter in open ground is not proposed for it (#147).
                FrostSuitabilityRule.of(crop, site, plot.kind().name()).orElse(null));
    }

    /** The crops growing in one area on the day: the neighbours the companion and antagonist checks read. */
    private static List<PlantingSummary> inArea(List<PlantingSummary> growing, PlotSummary plot) {
        return growing.stream().filter(p -> p.plotId() == plot.id()).toList();
    }

    /** The families of the crops growing in the garden on the day, for the VARIETY goal. */
    private Set<String> families(List<PlantingSummary> growing) {
        Set<String> families = new HashSet<>();
        for (PlantingSummary planting : growing) {
            crops.byId(planting.cropId()).map(Crop::family).ifPresent(families::add);
        }
        return families;
    }

    /** The garden of the space, whose day the proposals are for; another space's garden is unknown. */
    private GardenSummary garden(long spaceId, String login, long gardenId) {
        return gardens.garden(spaceId, login, gardenId)
                .orElseThrow(() -> new IllegalArgumentException("No such garden in this space"));
    }

    /** The current day at the garden (#158), never the machine's. */
    private LocalDate today(GardenSummary garden) {
        return GardenTime.today(garden, clock);
    }

    /**
     * The day the proposal is for: today in the current month, so an area planted this month is not free
     * (#141: after "Plant" the area leaves the list); otherwise the first day of the month's next occurrence.
     */
    static LocalDate firstDayOf(Month month, LocalDate today) {
        if (today.getMonth() == month) {
            return today;
        }
        LocalDate day = today.withDayOfMonth(1).withMonth(month.getValue());
        return day.isBefore(today.withDayOfMonth(1)) ? day.plusYears(1) : day;
    }

    /** A window covers the month, wrapping over the new year when it starts after it ends. */
    static boolean covers(int fromMonth, int toMonth, int month) {
        return fromMonth <= toMonth ? month >= fromMonth && month <= toMonth
                : month >= fromMonth || month <= toMonth;
    }
}