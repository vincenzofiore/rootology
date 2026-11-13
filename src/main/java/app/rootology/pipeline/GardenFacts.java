package app.rootology.pipeline;

import app.rootology.facts.ClimateWindows;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.Fact;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourcedFact;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.PlantingSummary;
import app.rootology.garden.RotationRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

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

    GardenFacts(GardenService gardens, KnowledgeGraph knowledge, ClimateWindows climate, CropCatalog crops,
                java.time.Clock clock) {
        this.clock = clock;
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

    /** What grows today, plus (organism, threatens, crop) for the pests and diseases active on it in the month. */
    List<SourcedFact> threats(QuestionContext context, RunContext run, String language) {
        List<PlantingSummary> growing = now(context, run);
        List<SourcedFact> facts = new ArrayList<>(growing(context, run, language));
        int m = Month.valueOf(context.intent().month()).getValue();
        growing.stream().map(PlantingSummary::cropId).distinct().forEach(cropId ->
                knowledge.affecting(cropId, m, context.spaceId()).forEach(a -> facts.add(new SourcedFact(new Fact(
                        "it".equals(language) ? a.italianName() : a.englishName(), "threatens", name(cropId, language)),
                        a.sources(), null, a.proposed()))));
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
