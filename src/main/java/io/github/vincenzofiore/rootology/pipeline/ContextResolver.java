package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Intent;
import io.github.vincenzofiore.rootology.decision.Note;
import io.github.vincenzofiore.rootology.decision.QueryKind;
import io.github.vincenzofiore.rootology.facts.ClimateCells;
import io.github.vincenzofiore.rootology.facts.FrostSeasonRule;
import io.github.vincenzofiore.rootology.facts.ReferenceSite;
import io.github.vincenzofiore.rootology.facts.SiteProfile;
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

/**
 * Resolves the context of a question once per run (#55, #62): the run's garden, the climate profile the
 * calendars read, the month with its precedence (what the question says, then the current month in Italy),
 * the plot a question names and the place of the forecast. Nothing else in the pipeline chooses a garden
 * any more.
 *
 * <p>A calendar question gets a climate profile: the reference site of a zone the question names, otherwise
 * the run's garden's own profile from its coordinates and altitude, otherwise the default (Vasto, CENTRO).
 * The profile applies to every garden with coordinates, CENTRO included: the curated windows are Vasto's,
 * the base of the derivation, not "the garden's zone". A garden abroad with a profile gets its derived
 * windows and the {@code WINDOWS_FROM_CLIMATE} note; {@code OUTSIDE_ITALY} stays only for a garden abroad
 * with no profile.
 *
 * <p>Ambiguity is never guessed: a garden question asked outside a garden, a garden that no longer exists
 * and a plot not found or matching several escalate deterministically, with the reason a person sees and a
 * note that says the specifics. Only a run that belongs to no space (the suite's) gets no note at all, so
 * its requests to the decision layer stay exactly as they were.
 */
@Component
class ContextResolver {

    /** The kinds that read the Italian calendars: only these get a climate profile and a month. */
    static final java.util.Set<QueryKind> NEEDS_ZONE = java.util.Set.of(QueryKind.SOWING_TIME,
            QueryKind.HARVEST_TIME, QueryKind.SOW_NOW, QueryKind.SOW_IN_PLOT);

    /** The kinds that need a month: the calendars and the pest months, which apply abroad too (#58). */
    static final java.util.Set<QueryKind> NEEDS_MONTH = java.util.Set.of(QueryKind.SOWING_TIME,
            QueryKind.HARVEST_TIME, QueryKind.SOW_NOW, QueryKind.SOW_IN_PLOT, QueryKind.PEST_RISK);

    /** The kinds about the run's garden: they cannot be answered outside one. */
    static final java.util.Set<QueryKind> NEEDS_GARDEN = java.util.Set.of(QueryKind.GROWING_NOW,
            QueryKind.PEST_RISK, QueryKind.SOW_IN_PLOT);

    /** The reasons of the escalations decided before the decision layer (#55); ReasonText translates them. */
    static final String NEEDS_GARDEN_REASON = "the question needs a garden";
    static final String GARDEN_GONE_REASON = "the garden no longer exists";
    static final String PLOT_REASON = "the plot is not in this garden";

    private final SpaceService spaces;
    private final GardenService gardens;
    private final ClimateCells cells;
    private final Clock clock;

    ContextResolver(SpaceService spaces, GardenService gardens, ClimateCells cells, Clock clock) {
        this.spaces = spaces;
        this.gardens = gardens;
        this.cells = cells;
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

        boolean asksZone = NEEDS_ZONE.contains(intent.kind());
        String gardenZone = garden == null ? null : GardenService.zone(garden).orElse(null);
        if (asksZone && intent.zone() != null && gardenZone != null && !intent.zone().equals(gardenZone)) {
            // The question wins and the person is told both zones; the calendars read the named zone's site (#55).
            notes.add(Note.of(Note.Kind.ZONE_OF_QUESTION, intent.zone(), gardenZone));
        }
        Intent effective = defaults(intent, current);

        SiteProfile climate = null;
        ContextSnapshot.ClimateFrom climateFrom = null;
        if (asksZone && !gone) {
            SiteProfile profile = climate(intent.zone(), garden);
            climate = profile;
            climateFrom = profile == null ? null : intent.zone() != null ? ContextSnapshot.ClimateFrom.QUESTION
                    : profile.fromGarden() ? ContextSnapshot.ClimateFrom.GARDEN : ContextSnapshot.ClimateFrom.DEFAULT;
            if (profile == null) {
                // No profile: abroad the calendars do not cover the place at all (#30), in Italy the
                // position has no cell within reach and the question has no windows to use (#62).
                notes.add(Note.of(abroad(garden) ? Note.Kind.OUTSIDE_ITALY : Note.Kind.NO_CLIMATE_PROFILE));
            } else if (profile.fromGarden()) {
                altitudeNote(profile, notes);
                if (abroad(garden)) {
                    // The calendar is the garden's own, not an Italian zone's (#62).
                    notes.add(Note.of(Note.Kind.WINDOWS_FROM_CLIMATE));
                }
            }
        }

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
                effective.zone(), intent.zone() != null ? ContextSnapshot.ZoneFrom.QUESTION : null,
                effective.month(), monthFrom(intent.month(), effective.month()), plotName(plot),
                climate == null ? null : climate.lastFrostDay(),
                climate == null ? null : climate.firstFrostDay(), climateFrom);
        return new QuestionContext(run.spaceId(), garden == null ? 0 : garden.id(), effective, plot,
                location(garden), climate, snapshot, refusal, notes);
    }

    /**
     * The climate profile of a calendar question: a zone named in the question uses its reference site;
     * otherwise the run's garden uses its own cell and altitude; otherwise the default is Vasto. Null when
     * the question names an unknown zone, or a garden abroad has no cell within reach.
     */
    private SiteProfile climate(String askedZone, GardenSummary garden) {
        if (askedZone != null) {
            return ReferenceSite.of(askedZone).map(site -> site.profile(cells)).orElse(null);
        }
        if (garden != null && garden.latitude() != null && garden.longitude() != null) {
            return cells.nearest(garden.latitude().doubleValue(), garden.longitude().doubleValue())
                    .map(cell -> FrostSeasonRule.profile(cell, garden.elevationM(), null)).orElse(null);
        }
        if (abroad(garden)) {
            // No coordinates abroad: no profile, and the calendars do not cover it.
            return null;
        }
        return ReferenceSite.CENTRO.profile(cells);
    }

    /** The notes that say what the altitude did to the frost dates (#62). */
    private static void altitudeNote(SiteProfile profile, List<Note> notes) {
        switch (profile.correction()) {
            case MISSING -> notes.add(Note.of(Note.Kind.ALTITUDE_MISSING));
            case COAST -> notes.add(Note.of(Note.Kind.ALTITUDE_COAST));
            case CAPPED -> notes.add(Note.of(Note.Kind.ALTITUDE_CAPPED));
            default -> {
            }
        }
    }

    /** True for a garden known to be outside Italy (a garden with no country is in Italy, saved before #30). */
    private static boolean abroad(GardenSummary garden) {
        return garden != null && garden.countryCode() != null && !garden.countryCode().equals("IT");
    }

    /** The plot as the answer used it: the found one by its own name, otherwise the name as asked. */
    private static String plotName(QuestionContext.Plot plot) {
        return plot == null ? null : plot.found() != null ? plot.found().name() : plot.askedName();
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
     * The month: the current month in Italy for the kinds that need one, so the pest months work abroad too
     * (#58); sowing and harvest windows keep no month unless the question names one. A calendar question
     * carries no zone by itself: the climate profile says where its windows come from (#62).
     */
    static Intent defaults(Intent intent, Month currentMonth) {
        if (!NEEDS_ZONE.contains(intent.kind()) && !NEEDS_MONTH.contains(intent.kind())) {
            return intent;
        }
        String month = intent.month();
        if (NEEDS_MONTH.contains(intent.kind()) && month == null
                && intent.kind() != QueryKind.SOWING_TIME && intent.kind() != QueryKind.HARVEST_TIME) {
            month = currentMonth.name();
        }
        // A kind that reads no zone (a pest question, #58) carries none, so nothing shows a zone that
        // played no part in the answer.
        String zone = NEEDS_ZONE.contains(intent.kind()) ? intent.zone() : null;
        return intent.withZoneAndMonth(zone, month);
    }
}
