package app.rootology.garden;

import app.rootology.facts.ClimateWindows;
import app.rootology.facts.ClimateWindows.ClimateWindow;
import app.rootology.facts.Crop;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.CropTraits;
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

    /** The proposals for the current month in Italy. */
    @Transactional(readOnly = true)
    public Proposals propose(long spaceId, String login, long gardenId, List<Goal> goals) {
        return propose(spaceId, login, gardenId, today().getMonth(), goals);
    }

    /** The proposals for a month: at most three crops for each free area of the garden. */
    @Transactional(readOnly = true)
    public Proposals propose(long spaceId, String login, long gardenId, Month month, List<Goal> goals) {
        CropProposalRule.checkGoals(goals);
        GardenSummary garden = gardens.garden(spaceId, login, gardenId)
                .orElseThrow(() -> new IllegalArgumentException("No such garden in this space"));
        Optional<SiteProfile> own = sites.profile(garden);
        SiteProfile site = own.orElseGet(sites::defaultProfile);
        LocalDate day = firstDayOf(month, today());
        List<PlantingSummary> growing = gardens.growingOn(spaceId, login, gardenId, day);
        Set<Long> busy = growing.stream().map(PlantingSummary::plotId).collect(Collectors.toSet());
        Set<String> families = families(growing);
        Map<String, List<Window>> windowsOfMonth = windowsOfMonth(site, month);
        List<AreaProposals> areas = new ArrayList<>();
        for (PlotSummary plot : gardens.plots(spaceId, login, gardenId)) {
            if (busy.contains(plot.id())) {
                continue;
            }
            List<CropFacts> candidates = candidates(spaceId, login, plot, day, growing, windowsOfMonth);
            areas.add(new AreaProposals(plot.id(), plot.name(), CropProposalRule.propose(
                    new AreaFacts(plot.id(), plot.name(), plot.directSunIn(month), families), candidates, goals)));
        }
        return new Proposals(garden.id(), garden.name(), month, own.isPresent(), areas);
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
     * The crops with a window this month, each with what the rule needs for this area: the rotation warning,
     * the antagonists growing in it, its light need and, for the goals, its cycle and service-plant rows.
     */
    private List<CropFacts> candidates(long spaceId, String login, PlotSummary plot, LocalDate day,
                                       List<PlantingSummary> growing, Map<String, List<Window>> windowsOfMonth) {
        List<PlantingSummary> history = gardens.plantings(spaceId, login, plot.id());
        Set<String> neighbours = growing.stream().filter(p -> p.plotId() == plot.id())
                .map(PlantingSummary::cropId).collect(Collectors.toSet());
        List<CropFacts> facts = new ArrayList<>();
        for (Map.Entry<String, List<Window>> entry : windowsOfMonth.entrySet()) {
            facts.add(cropFacts(entry.getKey(), entry.getValue(), history, neighbours, day));
        }
        return facts;
    }

    private CropFacts cropFacts(String cropId, List<Window> windows, List<PlantingSummary> history,
                                Set<String> neighbours, LocalDate day) {
        Crop crop = crops.byId(cropId).orElseThrow(() -> new IllegalStateException("Unknown crop: " + cropId));
        boolean antagonistInArea = neighbours.stream().anyMatch(other -> !other.equals(cropId)
                && traits.antagonists(cropId, other));
        Optional<CropTraits.Beneficial> beneficial = traits.beneficial(cropId);
        return new CropFacts(cropId, crop.family(), crop.service(),
                traits.cycleMinDays(cropId).orElse(null),
                traits.lightOptimum(cropId).flatMap(LightRule::needs).orElse(null),
                windows, rotation.check(history, cropId, day).isPresent(), antagonistInArea,
                beneficial.isPresent(), beneficial.map(CropTraits.Beneficial::traditionalOnly).orElse(false),
                beneficial.map(CropTraits.Beneficial::sources).orElse(List.of()), crop.perennial());
    }

    /** The families of the crops growing in the garden on the day, for the VARIETY goal. */
    private Set<String> families(List<PlantingSummary> growing) {
        Set<String> families = new HashSet<>();
        for (PlantingSummary planting : growing) {
            crops.byId(planting.cropId()).map(Crop::family).ifPresent(families::add);
        }
        return families;
    }

    /** The current day in the clock's zone (Italy), never the machine's. */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), clock.getZone());
    }

    /** The first day of the month's next occurrence (this month's when it is the current one). */
    static LocalDate firstDayOf(Month month, LocalDate today) {
        LocalDate day = today.withDayOfMonth(1).withMonth(month.getValue());
        return day.isBefore(today.withDayOfMonth(1)) ? day.plusYears(1) : day;
    }

    /** A window covers the month, wrapping over the new year when it starts after it ends. */
    static boolean covers(int fromMonth, int toMonth, int month) {
        return fromMonth <= toMonth ? month >= fromMonth && month <= toMonth
                : month >= fromMonth || month <= toMonth;
    }
}