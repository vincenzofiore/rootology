package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.Note;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.garden.GardenService;
import io.github.vincenzofiore.rootology.garden.GardenViews.GardenSummary;
import io.github.vincenzofiore.rootology.garden.GardenViews.PlotLookup;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the context of a question once per run (#55): the run's garden, the zone and the month with
 * their precedence (what the question says, then the run's garden, then the defaults), the plot a
 * question names and the place of the forecast. Nothing else in the pipeline chooses a garden any more.
 *
 * <p>Ambiguity is never guessed: a garden question asked outside a garden, a garden that no longer exists
 * and a plot not found or matching several escalate deterministically, with the reason a person sees and a
 * note that says the specifics. Only a run that belongs to no space (the suite's) gets no note at all, so
 * its requests to the decision layer stay exactly as they were.
 */
@Component
class ContextResolver {

    /** The kinds that read the Italian calendars: only these get a zone, and the outside-Italy note (#58). */
    static final Set<QueryKind> NEEDS_ZONE = Set.of(QueryKind.SOWING_TIME, QueryKind.HARVEST_TIME, QueryKind.SOW_NOW,
            QueryKind.SOW_IN_PLOT);

    /** The kinds that need a month: the calendars and the pest months, which apply abroad too (#58). */
    static final Set<QueryKind> NEEDS_MONTH = Set.of(QueryKind.SOWING_TIME, QueryKind.HARVEST_TIME,
            QueryKind.SOW_NOW, QueryKind.SOW_IN_PLOT, QueryKind.PEST_RISK);

    /** The kinds about the run's garden: they cannot be answered outside one. */
    static final Set<QueryKind> NEEDS_GARDEN = Set.of(QueryKind.GROWING_NOW, QueryKind.PEST_RISK,
            QueryKind.SOW_IN_PLOT);

    /** The reasons of the escalations decided before the decision layer (#55); ReasonText translates them. */
    static final String NEEDS_GARDEN_REASON = "the question needs a garden";
    static final String GARDEN_GONE_REASON = "the garden no longer exists";
    static final String PLOT_REASON = "the plot is not in this garden";

    private static final String DEFAULT_ZONE = "CENTRO";

    private final SpaceService spaces;
    private final GardenService gardens;
    private final Clock clock;

    ContextResolver(SpaceService spaces, GardenService gardens, Clock clock) {
        this.spaces = spaces;
        this.gardens = gardens;
        this.clock = clock;
    }

    QuestionContext resolve(Intent intent, RunContext run) {
        Month current = LocalDate.now(clock).getMonth();
        if (!run.hasSpace()) {
            // The suite's runs: no garden, no notes, nothing shown; only the defaults reach the facts.
            return QuestionContext.none(defaults(intent, current));
        }
        List<Note> notes = new ArrayList<>();
        GardenSummary garden = run.hasGarden()
                ? gardens.garden(run.spaceId(), run.startedBy(), run.gardenId()).orElse(null) : null;
        boolean gone = run.hasGarden() && garden == null;
        if (gone) {
            // A garden deleted between opening the page and asking: not the space level, never a guess (#55).
            notes.add(Note.of(Note.Kind.GARDEN_GONE));
        }
        String refusal = gone ? GARDEN_GONE_REASON : null;
        if (garden == null && !gone) {
            if (NEEDS_GARDEN.contains(intent.kind())) {
                notes.add(Note.of(Note.Kind.NO_GARDEN_FOR_QUESTION));
                refusal = NEEDS_GARDEN_REASON;
            } else {
                notes.add(Note.of(Note.Kind.NO_GARDEN_DEFAULTS));
            }
        }

        String gardenZone = garden == null ? null : GardenService.zone(garden).orElse(null);
        boolean asksZone = NEEDS_ZONE.contains(intent.kind());
        if (asksZone && intent.zone() != null && gardenZone != null && !intent.zone().equals(gardenZone)) {
            // The question wins, and the person is told both zones (#55).
            notes.add(Note.of(Note.Kind.ZONE_OF_QUESTION, intent.zone(), gardenZone));
        }
        Intent withGarden = asksZone && intent.zone() == null && gardenZone != null
                ? intent.withZoneAndMonth(gardenZone, intent.month()) : intent;
        Intent effective = defaults(withGarden, current);

        QuestionContext.Plot plot = null;
        if (intent.kind() == QueryKind.SOW_IN_PLOT && !gone) {
            plot = plot(intent.subject(), garden, run, notes);
            if (plot.outcome() == QuestionContext.Plot.Outcome.FOUND) {
                // The question is about the plot as it is really called, so the facts and the decision layer agree.
                effective = effective.withSubject(plot.found().name());
            } else if (refusal == null) {
                // At the space level the reason is the missing garden; inside a garden, the plot.
                refusal = PLOT_REASON;
            }
        }

        // A garden that no longer exists has nothing true to show: the note and the reason say it instead.
        ContextSnapshot snapshot = gone ? null : new ContextSnapshot(
                spaces.spaceName(run.spaceId(), run.startedBy()).orElse(null),
                garden == null ? null : garden.name(),
                effective.zone(), asksZone ? zoneFrom(intent.zone(), gardenZone) : null,
                effective.month(), monthFrom(intent.month(), effective.month()), plotName(plot));
        return new QuestionContext(run.spaceId(), garden == null ? 0 : garden.id(), effective, plot,
                location(garden), snapshot, refusal, notes);
    }

    /** The plot as the answer used it: the found one by its own name, otherwise the name as asked. */
    private static String plotName(QuestionContext.Plot plot) {
        return plot == null ? null : plot.found() != null ? plot.found().name() : plot.askedName();
    }

    private static ContextSnapshot.ZoneFrom zoneFrom(String askedZone, String gardenZone) {
        return askedZone != null ? ContextSnapshot.ZoneFrom.QUESTION
                : gardenZone != null ? ContextSnapshot.ZoneFrom.GARDEN : ContextSnapshot.ZoneFrom.DEFAULT;
    }

    private static ContextSnapshot.MonthFrom monthFrom(String askedMonth, String month) {
        return month == null ? null
                : askedMonth != null ? ContextSnapshot.MonthFrom.QUESTION : ContextSnapshot.MonthFrom.DEFAULT;
    }

    /** The plot of a SOW_IN_PLOT question, looked up only in the run's garden; several are never guessed. */
    private QuestionContext.Plot plot(String asked, GardenSummary garden, RunContext run, List<Note> notes) {
        String name = asked == null ? "" : asked.trim();
        if (garden == null) {
            return new QuestionContext.Plot(name, QuestionContext.Plot.Outcome.NOT_FOUND, null, List.of());
        }
        PlotLookup lookup = gardens.plotNamed(run.spaceId(), run.startedBy(), garden.id(), name);
        if (lookup.found()) {
            return new QuestionContext.Plot(name, QuestionContext.Plot.Outcome.FOUND, lookup.plot(), List.of());
        }
        if (lookup.ambiguous()) {
            notes.add(Note.of(Note.Kind.PLOT_AMBIGUOUS, name, garden.name(), String.join(", ", lookup.matches())));
            return new QuestionContext.Plot(name, QuestionContext.Plot.Outcome.AMBIGUOUS, null, lookup.matches());
        }
        notes.add(Note.of(Note.Kind.PLOT_NOT_FOUND, name, garden.name()));
        return new QuestionContext.Plot(name, QuestionContext.Plot.Outcome.NOT_FOUND, null, List.of());
    }

    private static QuestionContext.Location location(GardenSummary garden) {
        return garden == null || garden.latitude() == null || garden.longitude() == null ? null
                : new QuestionContext.Location(garden.latitude(), garden.longitude());
    }

    /**
     * Zone CENTRO unless already set, only for the kinds that read the Italian calendars; the current month
     * in Italy for the kinds that need a month, so the pest months work abroad too (#58). The kinds that
     * need no zone carry none, so nothing shows a zone that played no part in the answer.
     */
    static Intent defaults(Intent intent, Month currentMonth) {
        boolean needsZone = NEEDS_ZONE.contains(intent.kind());
        boolean needsMonth = NEEDS_MONTH.contains(intent.kind());
        if (!needsZone && !needsMonth) {
            return intent;
        }
        String zone = null;
        if (needsZone) {
            zone = intent.zone() == null ? DEFAULT_ZONE : intent.zone();
        }
        String month = intent.month();
        if (needsMonth && month == null && intent.kind() != QueryKind.SOWING_TIME
                && intent.kind() != QueryKind.HARVEST_TIME) {
            // Sowing and harvest windows keep no month unless the question names one.
            month = currentMonth.name();
        }
        return intent.withZoneAndMonth(zone, month);
    }
}
