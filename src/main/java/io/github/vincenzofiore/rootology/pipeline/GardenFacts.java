package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.facts.CropCatalog;
import io.github.vincenzofiore.rootology.facts.Fact;
import io.github.vincenzofiore.rootology.facts.KnowledgeGraph;
import io.github.vincenzofiore.rootology.facts.SourcedFact;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlantingSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlotSummary;
import io.github.vincenzofiore.rootology.garden.RotationRule;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Facts about the asking space's garden, for the questions about "my garden". Only the run's
 * space is read, and only when the run has one; garden facts have the source id `garden`.
 */
@Component
class GardenFacts {

    static final String SOURCE = "garden";

    private final GardenService gardens;
    private final KnowledgeGraph knowledge;
    private final CropCatalog crops;

    GardenFacts(GardenService gardens, KnowledgeGraph knowledge, CropCatalog crops) {
        this.gardens = gardens;
        this.knowledge = knowledge;
        this.crops = crops;
    }

    Optional<String> zone(long spaceId, String login) {
        return gardens.zone(spaceId, login);
    }

    /** (plot, grows, crop) for what grows today. */
    List<SourcedFact> growing(RunContext run, String language) {
        return now(run).stream().map(p -> new SourcedFact(new Fact(p.plotName(), "grows", name(p.cropId(), language)),
                List.of(SOURCE))).toList();
    }

    /** What grows today, plus (organism, threatens, crop) for the pests and diseases active on it in the month. */
    List<SourcedFact> threats(RunContext run, String month, String language) {
        List<PlantingSummary> growing = now(run);
        List<SourcedFact> facts = new ArrayList<>(growing(run, language));
        int m = Month.valueOf(month).getValue();
        growing.stream().map(PlantingSummary::cropId).distinct().forEach(cropId ->
                knowledge.affecting(cropId, m, run.spaceId()).forEach(a -> facts.add(new SourcedFact(new Fact(
                        "it".equals(language) ? a.italianName() : a.englishName(), "threatens", name(cropId, language)),
                        a.sources(), null, a.proposed()))));
        return facts;
    }

    /**
     * What can be sown or planted in the month and zone, plus (crop, avoidIn, plot) for each of those that
     * would follow a crop of the same family in the plot (rule family-rotation-v1). Empty if the plot is unknown.
     */
    List<SourcedFact> sowableIn(RunContext run, String plotName, String month, String zone, String language) {
        if (!run.hasSpace()) {
            return List.of();
        }
        Optional<PlotSummary> plot = gardens.plotByName(run.spaceId(), run.startedBy(), plotName);
        if (plot.isEmpty()) {
            return List.of();
        }
        List<SourcedFact> facts = new ArrayList<>();
        facts.add(new SourcedFact(new Fact(plotName.trim(), "hasSoil", plot.get().soil().name()), List.of(SOURCE)));
        LocalDate on = nextFirstDay(Month.valueOf(month));
        for (SourcedFact sowable : knowledge.sowingFacts(month, zone, language)) {
            facts.add(sowable);
            knowledge.resolve(sowable.fact().subject(), run.spaceId()).ifPresent(crop -> gardens
                    .rotationWarning(run.spaceId(), run.startedBy(), plot.get().id(), crop.id(), on)
                    .ifPresent(w -> facts.add(new SourcedFact(new Fact(sowable.fact().subject(), "avoidIn",
                            plotName.trim()), List.of(SOURCE), RotationRule.ID))));
        }
        return facts;
    }

    private List<PlantingSummary> now(RunContext run) {
        return run.hasSpace() ? gardens.growingOn(run.spaceId(), run.startedBy(), LocalDate.now(GardenPipelineAgent.ITALY))
                : List.of();
    }

    private String name(String cropId, String language) {
        return crops.byId(cropId).map(c -> "it".equals(language) ? c.italianName() : c.englishName()).orElse(cropId);
    }

    /** The first day of the month's next occurrence (today if it is the current month). */
    static LocalDate nextFirstDay(Month month) {
        LocalDate today = LocalDate.now(GardenPipelineAgent.ITALY);
        if (today.getMonth() == month) {
            return today;
        }
        LocalDate candidate = today.withDayOfMonth(1).withMonth(month.getValue());
        return candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
    }
}
