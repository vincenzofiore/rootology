package app.rootology.pipeline;

import app.rootology.decision.Intent;
import app.rootology.pipeline.QuestionContext.Choice;
import app.rootology.decision.Note;
import app.rootology.decision.QueryKind;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.FrostSeasonRule;
import app.rootology.facts.ReferenceSite;
import app.rootology.facts.SiteProfile;
import app.rootology.garden.GardenClimate;
import app.rootology.garden.GardenService;
import app.rootology.garden.GardenViews.GardenSummary;
import app.rootology.garden.GardenViews.PlotLookup;
import app.rootology.geocoding.GeoNames;
import app.rootology.geocoding.Place;
import app.rootology.spaces.SpaceService;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the context of a question once per run (#55, #62, #107): the run's garden, the climate profile the
 * calendars read, the month with its precedence (what the question says, then the current month in Italy),
 * the plot a question names, the place the question names and the place of the forecast. Nothing else in the
 * pipeline chooses a garden any more.
 *
 * <p>A calendar question gets a climate profile: a place named in the question, otherwise the reference site
 * of a zone it names, otherwise the run's garden's own profile from its coordinates and altitude, otherwise
 * the default (Vasto, CENTRO). The profile applies to every garden with coordinates, CENTRO included: the
 * curated windows are Vasto's, the base of the derivation, not "the garden's zone". A garden abroad with a
 * profile gets its derived windows and the {@code WINDOWS_FROM_CLIMATE} note; {@code OUTSIDE_ITALY} stays
 * only for a garden abroad with no profile.
 *
 * <p>A garden or place the question names (#57, #107: {@code Intent.garden}, {@code Intent.place}, read by
 * Claude from the space's names) wins over the page's, with a note when it differs; the run then belongs to
 * that garden. A name that matches no garden escalates, never guessed; a place that is not found or matches
 * several, and a garden name matching several, are a question the person answers by choosing (#107).
 *
 * <p>Ambiguity is never guessed: a garden question asked outside a garden and a plot not found or matching
 * several escalate deterministically, with the reason a person sees and a note that says the specifics. Only
 * a run that belongs to no space (the suite's) gets no note at all, so its requests to the decision layer
 * stay exactly as they were.
 */
@Component
class ContextResolver {

    /** The kinds that read the Italian calendars: only these get a climate profile and a month. */
    static final java.util.Set<QueryKind> NEEDS_ZONE = java.util.Set.of(QueryKind.SOWING_TIME,
            QueryKind.HARVEST_TIME, QueryKind.PRUNING_TIME, QueryKind.SOW_NOW, QueryKind.SOW_IN_PLOT,
            QueryKind.PROPOSE_IN_AREA);

    /** The kinds a place named in the question applies to (#107). */
    static final java.util.Set<QueryKind> NEEDS_PLACE = java.util.Set.of(QueryKind.SOWING_TIME,
            QueryKind.HARVEST_TIME, QueryKind.PRUNING_TIME, QueryKind.SOW_NOW);

    /** The kinds that need a month: the calendars and the pest months, which apply abroad too (#58). */
    static final java.util.Set<QueryKind> NEEDS_MONTH = java.util.Set.of(QueryKind.SOWING_TIME,
            QueryKind.HARVEST_TIME, QueryKind.PRUNING_TIME, QueryKind.SOW_NOW, QueryKind.SOW_IN_PLOT, QueryKind.PEST_RISK,
            QueryKind.PROPOSE_IN_AREA);

    /** The kinds about the run's garden: they cannot be answered outside one. */
    static final java.util.Set<QueryKind> NEEDS_GARDEN = java.util.Set.of(QueryKind.GROWING_NOW,
            QueryKind.PEST_RISK, QueryKind.SOW_IN_PLOT, QueryKind.PROPOSE_IN_AREA);

    /** The reasons of the escalations decided before the decision layer (#55); ReasonText translates them. */
    static final String GARDEN_GONE_REASON = "the garden no longer exists";
    static final String PLOT_REASON = "the plot is not in this garden";
    static final String GARDEN_NAME_REASON = "the garden named is not clear";

    /** A place wins over another one only with this much more population; otherwise the person chooses. */
    static final int CLEAR_WINNER_FACTOR = 10;
    /** At most this many places are offered to the person (#107). */
    static final int MAX_CANDIDATES = 5;
    /** At most this many names of each kind reach Claude, each cut to {@link #NAME_LENGTH} characters. */
    static final int MAX_NAMES = 30;
    static final int NAME_LENGTH = 60;

    /**
     * The names Claude may read besides the question (#57): the space's gardens and the current garden's
     * plots, nothing else (no coordinates, no plantings). Empty for a run that belongs to no space.
     */
    record Names(List<String> gardens, List<String> plots) {

        static final Names NONE = new Names(List.of(), List.of());

        boolean isEmpty() {
            return gardens.isEmpty() && plots.isEmpty();
        }
    }

    /** What a place named in a question resolved to (#107). */
    record PlaceLookup(Choice.Place place, List<Choice.Place> candidates, Outcome outcome) {

        enum Outcome {
            FOUND, NOT_FOUND, AMBIGUOUS
        }

        static PlaceLookup found(Choice.Place place) {
            return new PlaceLookup(place, List.of(), Outcome.FOUND);
        }

        static PlaceLookup notFound() {
            return new PlaceLookup(null, List.of(), Outcome.NOT_FOUND);
        }

        static PlaceLookup ambiguous(List<Choice.Place> candidates) {
            return new PlaceLookup(null, candidates, Outcome.AMBIGUOUS);
        }
    }

    private final SpaceService spaces;
    private final GardenService gardens;
    private final ClimateCells cells;
    private final GardenClimate sites;
    private final GeoNames geoNames;
    private final Clock clock;

    ContextResolver(SpaceService spaces, GardenService gardens, ClimateCells cells, GardenClimate sites,
                    GeoNames geoNames, Clock clock) {
        this.spaces = spaces;
        this.gardens = gardens;
        this.cells = cells;
        this.sites = sites;
        this.geoNames = geoNames;
        this.clock = clock;
    }

    /** The names of the run's space and garden for Claude (#57); none for the suite's runs. */
    Names names(RunContext run) {
        if (!run.hasSpace()) {
            return Names.NONE;
        }
        List<String> gardenNames = gardens.gardens(run.spaceId(), run.startedBy()).stream()
                .map(GardenSummary::name).map(ContextResolver::plainName).limit(MAX_NAMES).toList();
        List<String> plotNames = !run.hasGarden() || gardens.garden(run.spaceId(), run.startedBy(), run.gardenId()).isEmpty()
                ? List.of()
                : gardens.plots(run.spaceId(), run.startedBy(), run.gardenId()).stream()
                        .map(p -> plainName(p.name())).limit(MAX_NAMES).toList();
        return new Names(gardenNames, plotNames);
    }

    /** A name as data: one line, no quotes to break out of, short. */
    private static String plainName(String name) {
        String plain = name.replaceAll("[\\p{Cntrl}\"]", " ").strip();
        return plain.length() <= NAME_LENGTH ? plain : plain.substring(0, NAME_LENGTH);
    }

    QuestionContext resolve(Intent intent, RunContext run) {
        return resolve(intent, run, null);
    }

    /**
     * The context of a question, with the place a person chose for a continued question already resolved
     * (#107): the place is then never looked up again.
     */
    QuestionContext resolve(Intent intent, RunContext run, Choice.Place chosenPlace) {
        Month current = LocalDate.now(clock).getMonth();
        if (!run.hasSpace()) {
            // The suite's runs: no garden, no notes, nothing shown; only the defaults reach the facts.
            return QuestionContext.none(defaults(intent, current));
        }
        List<Note> notes = new ArrayList<>();
        GardenSummary page = run.hasGarden()
                ? gardens.garden(run.spaceId(), run.startedBy(), run.gardenId()).orElse(null) : null;
        GardenSummary garden = page;
        boolean gone = run.hasGarden() && page == null;
        String refusal = null;
        QuestionContext.Choice choice = null;
        // A garden the question names wins over the page's (#57), only for the kinds a garden changes.
        if (intent.garden() != null && (NEEDS_ZONE.contains(intent.kind()) || NEEDS_GARDEN.contains(intent.kind()))) {
            var named = gardens.gardenNamed(run.spaceId(), run.startedBy(), intent.garden());
            if (named.found()) {
                garden = named.garden();
                gone = false;
                if (page != null && page.id() != garden.id()) {
                    notes.add(Note.of(Note.Kind.GARDEN_OF_QUESTION, garden.name(), page.name()));
                }
            } else if (named.ambiguous()) {
                // Never guessed: the person picks one of the gardens the name matches (#107).
                choice = QuestionContext.Choice.garden(QuestionContext.Choice.Why.AMBIGUOUS,
                        named.matches().stream().map(g -> new QuestionContext.Choice.Garden(g.id(), g.name())).toList());
            } else {
                refusal = GARDEN_NAME_REASON;
                notes.add(Note.of(Note.Kind.GARDEN_NOT_FOUND, intent.garden()));
            }
        }
        if (choice == null && gone) {
            // A garden deleted between opening the page and asking: not the space level, never a guess (#55).
            notes.add(Note.of(Note.Kind.GARDEN_GONE));
            if (refusal == null) {
                refusal = GARDEN_GONE_REASON;
            }
        }
        if (choice == null && garden == null && !gone && refusal == null) {
            if (NEEDS_GARDEN.contains(intent.kind())) {
                // A garden question outside a garden: the person picks a garden of the space (#107).
                choice = QuestionContext.Choice.garden(QuestionContext.Choice.Why.MISSING, gardensOf(run));
            } else if (!NEEDS_PLACE.contains(intent.kind())) {
                // Only the kinds that can use the defaults say so: a calendar question asks for a place (#107).
                notes.add(Note.of(Note.Kind.NO_GARDEN_DEFAULTS));
            }
        }

        Intent effective = defaults(intent, current);
        boolean calendar = NEEDS_PLACE.contains(intent.kind());
        QuestionContext.Choice.Place place = null;
        if (calendar && choice == null && !gone && refusal == null) {
            if (chosenPlace != null) {
                // The person already chose it: no lookup, and no question again (#107).
                place = chosenPlace;
            } else if (intent.place() != null) {
                PlaceLookup lookup = lookup(intent.place(), effective.languageOrDefault());
                switch (lookup.outcome()) {
                    case FOUND -> place = lookup.place();
                    case AMBIGUOUS -> choice = QuestionContext.Choice.place(QuestionContext.Choice.Why.AMBIGUOUS,
                            lookup.candidates());
                    case NOT_FOUND -> {
                        if (garden != null) {
                            // Inside a garden the garden answers, with a note: a misread word is not worth a
                            // choice, and the garden is where the person is (#107).
                            notes.add(Note.of(Note.Kind.PLACE_NOT_FOUND, intent.place(), garden.name()));
                        } else {
                            choice = QuestionContext.Choice.place(QuestionContext.Choice.Why.NOT_FOUND,
                                    gardenTowns(run));
                        }
                    }
                }
            } else if (garden == null && effective.zone() == null) {
                // No place, no zone and no garden: the calendars need a place, the person picks one (#107).
                choice = QuestionContext.Choice.place(QuestionContext.Choice.Why.MISSING, gardenTowns(run));
            }
        }
        if (choice != null) {
            return new QuestionContext(run.spaceId(), 0, effective, null, null, null, null, null, notes, choice, null);
        }
        if (place != null && effective.zone() != null) {
            // The place wins over the zone the question also names: the zone plays no part (#107).
            effective = effective.withZoneAndMonth(null, effective.month());
        }

        SiteProfile climate = null;
        ContextSnapshot.ClimateFrom climateFrom = null;
        if (NEEDS_ZONE.contains(intent.kind()) && !gone) {
            if (place != null) {
                climate = placeProfile(place);
                climateFrom = climate == null ? null : ContextSnapshot.ClimateFrom.PLACE;
                if (climate == null) {
                    // No cell within reach: abroad the calendars do not cover the place, in Italy it has no
                    // profile at all (#62, #107).
                    notes.add(Note.of(abroad(place) ? Note.Kind.OUTSIDE_ITALY : Note.Kind.NO_CLIMATE_PROFILE));
                } else {
                    altitudeNote(climate, notes);
                    if (abroad(place)) {
                        // The calendar is the place's own, not an Italian zone's (#62).
                        notes.add(Note.of(Note.Kind.WINDOWS_FROM_CLIMATE));
                    }
                }
                if (garden != null) {
                    // Inside a garden a place named wins over it, and the person is told (#107).
                    notes.add(Note.of(Note.Kind.PLACE_OF_QUESTION, place.name(), garden.name()));
                }
            } else {
                SiteProfile profile = climate(effective.zone(), garden);
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
        }
        if (intent.kind() == QueryKind.PEST_RISK && !gone) {
            // A pest question has no calendar, but its months follow the crop's own calendar at the
            // garden's site (#162): the profile is read, with no note about windows.
            climate = climate(null, garden);
            climateFrom = climate == null ? null
                    : climate.fromGarden() ? ContextSnapshot.ClimateFrom.GARDEN : ContextSnapshot.ClimateFrom.DEFAULT;
        }

        boolean asksZone = NEEDS_ZONE.contains(intent.kind());
        String gardenZone = garden == null ? null : GardenService.zone(garden).orElse(null);
        if (asksZone && place == null && intent.zone() != null && gardenZone != null
                && !intent.zone().equals(gardenZone)) {
            // The question wins and the person is told both zones; the calendars read the named zone's site (#55).
            notes.add(Note.of(Note.Kind.ZONE_OF_QUESTION, intent.zone(), gardenZone));
        }

        QuestionContext.Plot plot = null;
        if ((intent.kind() == QueryKind.SOW_IN_PLOT || intent.kind() == QueryKind.PROPOSE_IN_AREA) && !gone) {
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
                effective.zone(), intent.zone() != null && place == null ? ContextSnapshot.ZoneFrom.QUESTION : null,
                effective.month(), monthFrom(intent.month(), effective.month()), plotName(plot),
                climate == null ? null : climate.lastFrostDay(),
                climate == null ? null : climate.firstFrostDay(), climateFrom,
                place == null ? null : place.name());
        return new QuestionContext(run.spaceId(), garden == null ? 0 : garden.id(), effective, plot,
                place != null ? new QuestionContext.Location(place.latitude(), place.longitude(), place.elevationM())
                        : location(garden),
                climate, snapshot, refusal, notes, null, place);
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
            return sites.profile(garden).orElse(null);
        }
        if (abroad(garden)) {
            // No coordinates abroad: no profile, and the calendars do not cover it.
            return null;
        }
        return sites.defaultProfile();
    }

    /**
     * A place named or chosen (#107): a place's own profile, from its coordinates and altitude as for a
     * garden, or the altitude looked up once when the list of candidates did not carry it.
     */
    private SiteProfile placeProfile(QuestionContext.Choice.Place place) {
        Integer altitude = place.elevationM();
        if (altitude == null) {
            altitude = geoNames.elevation(place.latitude().doubleValue(), place.longitude().doubleValue())
                    .orElse(null);
        }
        Integer metres = altitude;
        return cells.nearest(place.latitude().doubleValue(), place.longitude().doubleValue())
                .map(cell -> FrostSeasonRule.profile(cell, metres, null))
                .orElse(null);
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

    /** True for a garden or place known to be outside Italy (no country is in Italy, saved before #30). */
    private static boolean abroad(GardenSummary garden) {
        return garden != null && garden.countryCode() != null && !garden.countryCode().equals("IT");
    }

    private static boolean abroad(QuestionContext.Choice.Place place) {
        return place.countryCode() != null && !place.countryCode().equals("IT");
    }

    /** The plot as the answer used it: the found one by its own name, otherwise the name as asked. */
    private static String plotName(QuestionContext.Plot plot) {
        return plot == null ? null : plot.found() != null ? plot.found().name() : plot.askedName();
    }

    private static ContextSnapshot.MonthFrom monthFrom(String askedMonth, String month) {
        return month == null ? null
                : askedMonth != null ? ContextSnapshot.MonthFrom.QUESTION : ContextSnapshot.MonthFrom.DEFAULT;
    }

    /** The gardens of the space the person may pick from (#107), for a garden question outside a garden. */
    private List<QuestionContext.Choice.Garden> gardensOf(RunContext run) {
        return gardens.gardens(run.spaceId(), run.startedBy()).stream()
                .map(g -> new QuestionContext.Choice.Garden(g.id(), g.name())).toList();
    }

    /**
     * The distinct towns of the space's gardens (#107): what a calendar question outside a garden may be
     * asked for, with the coordinates and altitude of the garden's own site.
     */
    private List<QuestionContext.Choice.Place> gardenTowns(RunContext run) {
        Map<String, QuestionContext.Choice.Place> towns = new LinkedHashMap<>();
        for (GardenSummary g : gardens.gardens(run.spaceId(), run.startedBy())) {
            if (g.town() == null || g.town().isBlank() || g.latitude() == null || g.longitude() == null) {
                continue;
            }
            String town = g.town().strip();
            towns.putIfAbsent(town.toLowerCase(Locale.ROOT), new QuestionContext.Choice.Place(town, null,
                    g.countryCode(), null, g.latitude(), g.longitude(), g.elevationM()));
        }
        return List.copyOf(towns.values());
    }

    /** The place a question names, found, not found or matching several (#107). */
    private PlaceLookup lookup(String asked, String language) {
        return choose(geoNames.search(asked, language), asked);
    }

    /**
     * The place rule (#107), without GeoNames: among the results, the exact name (case and accents ignored,
     * Italy first); one match is the place; several are the most populous when it has at least ten times the
     * second's population, otherwise the person chooses among up to five of them. With no exact name, the
     * places whose names start with it are the person's choice (Italy first, then by population); only no
     * result at all is not found.
     */
    static PlaceLookup choose(List<Place> found, String asked) {
        String wanted = plain(asked);
        List<Place> exact = found.stream().filter(p -> plain(p.name()).equals(wanted)).toList();
        if (exact.isEmpty()) {
            // No town has exactly that name: the places whose names start with it ("Sant'Angelo Lodigiano"
            // for "Sant'Angelo") are the person's choice; nothing at all is not found.
            return found.isEmpty() ? PlaceLookup.notFound() : PlaceLookup.ambiguous(found.stream()
                    .sorted(Comparator.comparing((Place p) -> !"IT".equals(p.countryCode()))
                            .thenComparing(Comparator.comparingLong(Place::population).reversed()))
                    .limit(MAX_CANDIDATES).map(p -> choosable(p, null)).toList());
        }
        List<Place> italian = exact.stream().filter(p -> "IT".equals(p.countryCode())).toList();
        List<Place> pool = italian.isEmpty() ? exact : italian;
        List<Place> byPopulation = pool.stream()
                .sorted(Comparator.comparingLong(Place::population).reversed()).toList();
        Place first = byPopulation.getFirst();
        if (byPopulation.size() == 1
                || first.population() >= (long) CLEAR_WINNER_FACTOR * byPopulation.get(1).population()) {
            return PlaceLookup.found(choosable(first, null));
        }
        return PlaceLookup.ambiguous(byPopulation.stream().limit(MAX_CANDIDATES)
                .map(p -> choosable(p, null)).toList());
    }

    private static QuestionContext.Choice.Place choosable(Place place, Integer elevationM) {
        return new QuestionContext.Choice.Place(place.name(), place.region(), place.countryCode(),
                place.countryName(), java.math.BigDecimal.valueOf(place.latitude()),
                java.math.BigDecimal.valueOf(place.longitude()), elevationM);
    }

    /** A name with its case and accents taken away, so "Forlì" and "Forli" are one name. */
    private static String plain(String text) {
        if (text == null) {
            return "";
        }
        // The typographic apostrophe of a question is the plain one of GeoNames: "Sant’Angelo" is "Sant'Angelo".
        String decomposed = Normalizer.normalize(text.trim().replaceAll("[\u2018\u2019`\u00b4]", "'"),
                Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
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
                : new QuestionContext.Location(garden.latitude(), garden.longitude(), garden.elevationM());
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
                && intent.kind() != QueryKind.SOWING_TIME && intent.kind() != QueryKind.HARVEST_TIME
                && intent.kind() != QueryKind.PRUNING_TIME) {
            month = currentMonth.name();
        }
        // A kind that reads no zone (a pest question, #58) carries none, so nothing shows a zone that
        // played no part in the answer.
        String zone = NEEDS_ZONE.contains(intent.kind()) ? intent.zone() : null;
        return intent.withZoneAndMonth(zone, month);
    }
}
