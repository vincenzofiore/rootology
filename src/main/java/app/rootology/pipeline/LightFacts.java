package app.rootology.pipeline;

import app.rootology.decision.Note;
import app.rootology.decision.Reading;
import app.rootology.facts.Fact;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourcedFact;
import app.rootology.garden.DirectSun;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.PlotSummary;
import app.rootology.garden.LightRule;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * light-need-v1 on what could be sown or planted (#54): `SOW_IN_PLOT` warns about crops asking for more light
 * than the named plot gets, `SOW_NOW` only when none of the run's garden's plots is bright enough (it does not
 * ask about one plot). The plot and the garden come from the {@link QuestionContext} (#55). Crops whose light
 * need is unknown are named in a note, never guessed. The area's direct sun is the one of the season of the
 * question (#89): the level from that season's hours when they are declared, otherwise the declared level,
 * and nothing when neither is known. Only the direct sun behind an exclusion reaches the decision layer,
 * tagged DECLARED (declared, not measured); orientation, wind and floor are kept for later rules and never
 * sent.
 */
@Component
class LightFacts {

    /** Readings to add and notes to show; both empty when the light rule does not apply. */
    record Result(List<Reading> readings, List<Note> notes) {
        static final Result NONE = new Result(List.of(), List.of());
    }

    private final GardenService gardens;
    private final KnowledgeGraph knowledge;
    private final Clock clock;

    LightFacts(GardenService gardens, KnowledgeGraph knowledge, Clock clock) {
        this.gardens = gardens;
        this.knowledge = knowledge;
        this.clock = clock;
    }

    /**
     * The month the question is about: the one the context carries (the intent already resolved it, for
     * example for SOW_IN_PLOT and SOW_NOW), the current one in Italy otherwise (#58).
     */
    private Month monthOf(QuestionContext context) {
        String asked = context.intent() == null ? null : context.intent().month();
        if (asked != null) {
            try {
                return Month.valueOf(asked.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                // An unknown month finds no facts and escalates anyway; the month of the run is a safe guess.
            }
        }
        return LocalDate.now(clock).getMonth();
    }

    /** The named plot's declared exposure, and light-need-v1 on the crops that could be sown in it. */
    Result inPlot(QuestionContext context, List<SourcedFact> sowable) {
        if (!context.hasSpace() || context.plot() == null || context.plot().found() == null) {
            return Result.NONE;
        }
        PlotSummary plot = context.plot().found();
        DirectSun sun = plot.directSunIn(monthOf(context));
        if (sun == null) {
            return Result.NONE;
        }
        List<Reading> readings = new ArrayList<>();
        Map<String, String> lightNeeds = knowledge.lightNeeds();
        List<String> avoided = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String crop : subjects(sowable)) {
            Optional<DirectSun> needs = needs(crop, context, lightNeeds);
            if (needs.isEmpty()) {
                unknown.add(crop);
            } else if (LightRule.tooDark(sun, needs.get())) {
                avoided.add(crop);
                readings.add(Reading.inferred(new Fact(crop, "avoidIn", plot.name()), LightRule.ID,
                        List.of(LightRule.GARDEN_SOURCE, LightRule.ECOCROP_SOURCE)));
            }
        }
        List<Note> notes = new ArrayList<>();
        if (!avoided.isEmpty()) {
            readings.addFirst(sunOf(plot, sun));
            notes.add(Note.of(Note.Kind.LIGHT_IN_PLOT, plot.name(), String.join(", ", avoided), LightRule.ID));
        }
        if (!unknown.isEmpty()) {
            notes.add(Note.of(Note.Kind.LIGHT_UNKNOWN, String.join(", ", unknown)));
        }
        return new Result(readings, notes);
    }

    /**
     * light-need-v1 for "what to sow now" in the run's garden: a crop is left out only when no plot of that
     * garden with declared sun gives it enough light; if one plot is bright enough, it can be sown there.
     */
    Result now(QuestionContext context, RunContext run, List<SourcedFact> sowable) {
        if (!context.hasSpace() || context.gardenId() == 0) {
            return Result.NONE;
        }
        Month month = monthOf(context);
        List<PlotSummary> plots = gardens.plots(context.spaceId(), run.startedBy(), context.gardenId()).stream()
                .filter(p -> p.directSunIn(month) != null).toList();
        if (plots.isEmpty()) {
            return Result.NONE;
        }
        List<DirectSun> sun = plots.stream().map(p -> p.directSunIn(month)).toList();
        List<Reading> readings = new ArrayList<>();
        Map<String, String> lightNeeds = knowledge.lightNeeds();
        List<String> avoided = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String crop : subjects(sowable)) {
            Optional<DirectSun> needs = needs(crop, context, lightNeeds);
            if (needs.isEmpty()) {
                unknown.add(crop);
            } else if (sun.stream().allMatch(plot -> LightRule.tooDark(plot, needs.get()))) {
                avoided.add(crop);
                readings.add(Reading.inferred(new Fact(crop, "avoidNow", "light"), LightRule.ID,
                        List.of(LightRule.GARDEN_SOURCE, LightRule.ECOCROP_SOURCE)));
            }
        }
        List<Note> notes = new ArrayList<>();
        if (!avoided.isEmpty()) {
            readings.addAll(0, plots.stream().map(p -> sunOf(p, p.directSunIn(month))).toList());
            notes.add(Note.of(Note.Kind.LIGHT_ANYWHERE, String.join(", ", avoided), LightRule.ID));
        }
        if (!unknown.isEmpty()) {
            notes.add(Note.of(Note.Kind.LIGHT_UNKNOWN, String.join(", ", unknown)));
        }
        return new Result(readings, notes);
    }

    /** The crop's least direct sun according to ECOCROP; empty when the need is not known. */
    private Optional<DirectSun> needs(String crop, QuestionContext context, Map<String, String> lightNeeds) {
        return knowledge.resolve(crop, context.spaceId()).map(KnowledgeGraph.Entity::id)
                .map(lightNeeds::get).flatMap(LightRule::needs);
    }

    private static List<String> subjects(List<SourcedFact> sowable) {
        return sowable.stream().filter(f -> app.rootology.facts.SowingActivity.PREDICATES
                        .contains(f.fact().predicate()))
                .map(f -> f.fact().subject()).distinct().toList();
    }

    /**
     * The direct sun the area gets in the question's season, a DECLARED reading with the `garden` source: its
     * level, or the level its hours give (#89).
     */
    private static Reading sunOf(PlotSummary plot, DirectSun sun) {
        return new Reading(new Fact(plot.name(), "hasDirectSun", sun.name()), Reading.Source.DECLARED,
                List.of(LightRule.GARDEN_SOURCE), null);
    }
}
