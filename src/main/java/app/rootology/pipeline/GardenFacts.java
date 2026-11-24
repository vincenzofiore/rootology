package app.rootology.pipeline;

import app.rootology.decision.Note;
import app.rootology.decision.QueryKind;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.garden.PlotKind;
import app.rootology.facts.ClimateWindows;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.CuratedKnowledge.WindowRow;
import app.rootology.facts.Fact;
import app.rootology.facts.FrostSeasonRule;
import app.rootology.facts.FrostShiftRule;
import app.rootology.facts.FrostSuitabilityRule;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.PestShiftRule;
import app.rootology.facts.SourcedFact;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.RotationRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Facts about the run's garden, for the questions about "my garden" (#55): only that garden's plots and
 * plantings are read, and only when the run has one; garden facts have the source id `garden`. The garden,
 * its climate profile, the month and the named plot come from the {@link QuestionContext}.
 */
@Component
class GardenFacts {

    static final String SOURCE = "garden";

    private final GardenService gardens;
    private final KnowledgeGraph knowledge;
    private final ClimateWindows climate;
    private final CropCatalog crops;
    private final java.time.Clock clock;
    private final app.rootology.garden.CropProposalService proposals;

    GardenFacts(GardenService gardens, KnowledgeGraph knowledge, ClimateWindows climate, CropCatalog crops,
                java.time.Clock clock, app.rootology.garden.CropProposalService proposals) {
        this.clock = clock;
        this.proposals = proposals;
        this.gardens = gardens;
        this.knowledge = knowledge;
        this.climate = climate;
        this.crops = crops;
    }

    /** (plot, grows, crop) for what grows today in the run's garden. */
    List<SourcedFact> growing(QuestionContext context, RunContext run, String language) {
        return now(context, run).stream().map(p -> new SourcedFact(new Fact(p.plotName(), "grows",
                name(p.cropId(), language)), List.of(SOURCE))).toList();
    }

    /**
     * The proposal of crop-proposal-v1 for the free area the question names (#141): for each proposed crop
     * (crop, sowIn|transplantIn|plantIn, MONTH) with its window's rule, and (crop, proposedFor, area),
     * (crop, meetsGoal, GOAL) and (crop, uncertainty, KIND) derived by crop-proposal-v1, but EDITORIAL_WINDOW. Empty when the
     * area is not found or not free: nothing is proposed, and the decision layer escalates.
     */
    List<SourcedFact> proposals(QuestionContext context, RunContext run, String language) {
        if (!context.hasSpace() || context.plot() == null || context.plot().found() == null) {
            return List.of();
        }
        var area = context.plot().found();
        List<app.rootology.garden.CropProposalRule.Goal> goals = context.intent().goals().stream()
                .map(app.rootology.garden.CropProposalRule.Goal::valueOf).toList();
        String rule = app.rootology.garden.CropProposalRule.ID;
        List<SourcedFact> facts = new ArrayList<>();
        proposals.propose(run.spaceId(), run.startedBy(), context.gardenId(), Month.valueOf(context.intent().month()), goals)
                .areas().stream().filter(a -> a.areaId() == area.id()).flatMap(a -> a.proposals().stream())
                .forEach(p -> {
                    String crop = name(p.cropId(), language);
                    facts.add(new SourcedFact(new Fact(crop, app.rootology.facts.SowingActivity.valueOf(p.activity()).predicate(),
                            Month.of(p.month()).name()), p.sources(), p.derived() ? app.rootology.facts.FrostShiftRule.ID : null));
                    facts.add(new SourcedFact(new Fact(crop, "proposedFor", area.name()), p.sources(), rule));
                    p.goals().stream().sorted().forEach(g -> facts.add(
                            new SourcedFact(new Fact(crop, "meetsGoal", g.name()), p.sources(), rule)));
                    // An editorial window is said by its source (rootology-editorial), as for SOW_IN_PLOT: flagged
                    // again it made Jev doubt nearly every proposal (#141). The screen still shows it.
                    p.uncertainties().stream().filter(u -> u != app.rootology.garden.CropProposalRule.Uncertainty.EDITORIAL_WINDOW)
                            .forEach(u -> facts.add(
                            new SourcedFact(new Fact(crop, "uncertainty", u.name()), p.sources(), rule)));
                });
        return facts;
    }

    /** What grows today, plus (organism, threatens, crop) for the pests and diseases active on it in the month. */
    List<SourcedFact> threats(QuestionContext context, RunContext run, String language) {
        List<PlantingSummary> growing = now(context, run);
        List<SourcedFact> facts = new ArrayList<>(growing(context, run, language));
        int m = Month.valueOf(context.intent().month()).getValue();
        // The pest months follow the crop's calendar at the garden's site (#162); without a profile they stay curated.
        List<WindowRow> curated = climate.curatedWindows();
        List<FrostShiftRule.InferredWindow> derived = climate.derivedWindows(context.climate());
        for (String cropId : growing.stream().map(PlantingSummary::cropId).distinct().toList()) {
            for (KnowledgeGraph.Affecting host : knowledge.hosts(cropId, context.spaceId())) {
                // A member's proposed row stays as written, so it keeps its PROPOSED reading for Jev and the person.
                PestShiftRule.Shifted months = host.proposed()
                        ? new PestShiftRule.Shifted(host.fromMonth(), host.toMonth(), null)
                        : PestShiftRule.shift(cropId, host.fromMonth(), host.toMonth(), curated, derived);
                if (!PestShiftRule.covers(months.fromMonth(), months.toMonth(), m)) {
                    continue;
                }
                facts.add(new SourcedFact(new Fact(
                        "it".equals(language) ? host.italianName() : host.englishName(), "threatens",
                        name(cropId, language)), host.sources(), months.derivedBy(), host.proposed()));
            }
        }
        return facts;
    }

    /**
     * What can be sown or planted in the month and zone, plus (crop, avoidIn, plot) for each of those that
     * would follow a crop of the same family in the plot (rule family-rotation-v1). Empty if the plot is
     * not known: the question cannot be answered and waits for a person (#55).
     */
    List<SourcedFact> sowableIn(QuestionContext context, RunContext run, String language) {
        if (!context.hasSpace() || context.plot() == null || context.plot().found() == null) {
            return List.of();
        }
        String plotName = context.plot().found().name();
        List<SourcedFact> facts = new ArrayList<>();
        facts.add(new SourcedFact(new Fact(plotName, "hasSoil", context.plot().found().soil().name()),
                List.of(SOURCE)));
        LocalDate on = nextFirstDay(Month.valueOf(context.intent().month()), LocalDate.now(clock));
        for (SourcedFact sowable : climate.sowingFacts(context.intent().month(), context.climate(), language)) {
            facts.add(sowable);
            knowledge.resolve(sowable.fact().subject(), context.spaceId()).ifPresent(crop -> gardens
                    .rotationWarning(context.spaceId(), run.startedBy(), context.plot().found().id(), crop.id(), on)
                    .ifPresent(w -> facts.add(new SourcedFact(new Fact(sowable.fact().subject(), "avoidIn",
                            plotName), List.of(SOURCE), RotationRule.ID))));
        }
        return facts;
    }

    /** The facts of frost-suitability-v1 and the notes that say them, for one question (#147). */
    record FrostSuitability(List<SourcedFact> facts, List<Note> notes) {

        static final FrostSuitability NONE = new FrostSuitability(List.of(), List.of());
    }

    /** The question kinds with a site and a crop, where frost-suitability-v1 can say something (#147). */
    private static final Set<QueryKind> FROST_KINDS = Set.of(QueryKind.SOWING_TIME, QueryKind.SOW_NOW,
            QueryKind.SOW_IN_PLOT, QueryKind.PROPOSE_IN_AREA);

    /** One crop's fact and note, when the rule has something to say about it. */
    private record Said(SourcedFact fact, Note note) {
    }

    /**
     * Rule frost-suitability-v1 (#147) on the perennials a question about sowing or planting is about: a crop
     * that does not stand the site's 1-in-5 winter minimum becomes an INFERRED reading (crop, survivesWinter,
     * no|only in a pot) with the crop's hardiness source and the site's, and a note that quotes the two numbers
     * and the verdict. In a pot or a planter the verdict is to shelter it in winter, in a greenhouse the rule
     * says nothing, and open ground (a plot in the ground or a raised bed, or no plot at all) does not survive
     * it. A crop that stands the winter says nothing.
     */
    FrostSuitability frost(QuestionContext context, RunContext run, List<SourcedFact> facts) {
        if (!FROST_KINDS.contains(context.intent().kind()) || context.climate() == null) {
            return FrostSuitability.NONE;
        }
        String areaKind = areaKind(context, run);
        Long space = context.hasSpace() ? context.spaceId() : null;
        List<SourcedFact> added = new ArrayList<>();
        List<Note> notes = new ArrayList<>();
        for (String subject : facts.stream().map(f -> f.fact().subject()).distinct().toList()) {
            frostOf(subject, context, space, areaKind).ifPresent(said -> {
                added.add(said.fact());
                notes.add(said.note());
            });
        }
        return new FrostSuitability(added, notes);
    }

    /**
     * The area the verdict is for: the question's own area when it names one; otherwise, in a garden whose
     * areas are all pots or planters, a pot (its perennials can be moved); otherwise open ground (null).
     */
    private String areaKind(QuestionContext context, RunContext run) {
        if ((context.intent().kind() == QueryKind.SOW_IN_PLOT || context.intent().kind() == QueryKind.PROPOSE_IN_AREA)
                && context.plot() != null && context.plot().found() != null) {
            return context.plot().found().kind().name();
        }
        if (!context.hasSpace() || context.gardenId() <= 0) {
            return null;
        }
        List<PlotSummary> plots = gardens.plots(context.spaceId(), run.startedBy(), context.gardenId());
        boolean onlyPots = !plots.isEmpty() && plots.stream()
                .allMatch(p -> p.kind() == PlotKind.POT || p.kind() == PlotKind.PLANTER);
        return onlyPots ? PlotKind.POT.name() : null;
    }

    /** What the rule says about one subject of the question, or empty when it says nothing (#147). */
    private Optional<Said> frostOf(String subject, QuestionContext context, Long space, String areaKind) {
        var crop = knowledge.resolve(subject, space).flatMap(found -> crops.byId(found.id()));
        if (crop.isEmpty()) {
            return Optional.empty();
        }
        var result = FrostSuitabilityRule.of(crop.get(), context.climate(), areaKind)
                .filter(found -> !found.survives());
        if (result.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        sources.add(crop.get().hardinessSource());
        sources.add(FrostSeasonRule.AGERA5);
        SourcedFact fact = new SourcedFact(new Fact(subject, "survivesWinter",
                result.get().verdict() == FrostSuitabilityRule.Verdict.SHELTER_IN_POT
                        ? "only in a pot, sheltered in winter" : "no"),
                List.copyOf(sources), FrostSuitabilityRule.ID);
        Note note = Note.of(Note.Kind.FROST_SUITABILITY, subject,
                String.format(Locale.ROOT, "%.1f", result.get().hardiness()),
                String.format(Locale.ROOT, "%.1f", result.get().winterMinimum()),
                result.get().verdict().name());
        return Optional.of(new Said(fact, note));
    }

    private List<PlantingSummary> now(QuestionContext context, RunContext run) {
        return context.hasSpace() && context.gardenId() > 0
                ? gardens.growingOn(context.spaceId(), run.startedBy(), context.gardenId(), LocalDate.now(clock))
                : List.of();
    }

    private String name(String cropId, String language) {
        return crops.byId(cropId).map(c -> "it".equals(language) ? c.italianName() : c.englishName()).orElse(cropId);
    }

    /** The first day of the month's next occurrence (today if it is the current month). */
    static LocalDate nextFirstDay(Month month, LocalDate today) {
        if (today.getMonth() == month) {
            return today;
        }
        LocalDate candidate = today.withDayOfMonth(1).withMonth(month.getValue());
        return candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
    }
}
