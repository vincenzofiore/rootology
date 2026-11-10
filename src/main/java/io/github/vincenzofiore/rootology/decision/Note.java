package io.github.vincenzofiore.rootology.decision;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Something the person should know about the facts behind an answer (#31), as data: a kind and its values,
 * so the UI can word and show it in the page's language. Dates are ISO strings; staleSince is an ISO local
 * date-time of an old forecast copy, or empty. {@link #english()} is the wording for Claude's prompt, tests and
 * logs. Never sent to Jev.
 */
public record Note(Kind kind, List<String> args) {

    public enum Kind {
        /** args: minimum (°C, one decimal), day, staleSince. */
        FORECAST_MINIMUM,
        /** args: rule, crops (comma-separated). */
        FROST_EXCLUDED,
        /** args: crops (comma-separated). */
        FROST_UNKNOWN,
        /** args: day the period ends, crops, rule, staleSince. */
        SMITH_PERIOD,
        /** args: staleSince. */
        NO_SMITH_PERIOD,
        FORECAST_UNAVAILABLE_FROST,
        FORECAST_UNAVAILABLE_RISK,
        NO_FORECAST_DAYS,
        NO_COORDINATES_FROST,
        NO_COORDINATES_RISK,
        OUTSIDE_ITALY,
        /** args: plot, crops (comma-separated), rule. */
        LIGHT_IN_PLOT,
        /** args: crops (comma-separated), rule. */
        LIGHT_ANYWHERE,
        /** args: crops (comma-separated). */
        LIGHT_UNKNOWN,
        /** args: the zone of the question, the zone of the garden. */
        ZONE_OF_QUESTION,
        /** The question was asked outside a garden: the defaults were used (#55). */
        NO_GARDEN_DEFAULTS,
        /** A garden question was asked outside a garden: it cannot be answered (#55). */
        NO_GARDEN_FOR_QUESTION,
        /** The garden the page was opened in no longer exists (#55). */
        GARDEN_GONE,
        /** args: plot as asked, garden. */
        PLOT_NOT_FOUND,
        /** args: plot as asked, garden, the plots matching (comma-separated). */
        PLOT_AMBIGUOUS,
        /** The windows were derived from the garden's climate profile (#62). */
        WINDOWS_FROM_CLIMATE,
        /** The garden has coordinates but no climate cell within reach (#62). */
        NO_CLIMATE_PROFILE,
        /** The garden's altitude is not known: the cell's frost dates were not corrected (#62). */
        ALTITUDE_MISSING,
        /** The garden is on the coast below its cell: the cell's frost dates were not corrected (#62). */
        ALTITUDE_COAST,
        /** The altitude difference was capped at 500 m (#62). */
        ALTITUDE_CAPPED
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter STALE = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

    public Note {
        args = List.copyOf(args);
    }

    public static Note of(Kind kind, String... args) {
        return new Note(kind, List.of(args));
    }

    public String arg(int i) {
        return i < args.size() ? args.get(i) : "";
    }

    public String english() {
        return switch (kind) {
            case FORECAST_MINIMUM -> "Forecast minimum in the next 7 days: " + arg(0) + " °C on "
                    + DAY.format(LocalDate.parse(arg(1))) + " (MET Norway" + stale(arg(2)) + ").";
            case FROST_EXCLUDED -> "Not advised now because of frost risk (" + arg(0) + "): " + arg(1) + ".";
            case FROST_UNKNOWN -> "Frost tolerance not known, not checked: " + arg(0) + ".";
            case SMITH_PERIOD -> "Smith period ending " + DAY.format(LocalDate.parse(arg(0))) + ": the weather favours "
                    + "late blight on " + arg(1) + " (" + arg(2) + ", MET Norway" + stale(arg(3)) + ").";
            case NO_SMITH_PERIOD -> "No Smith period around today (MET Norway" + stale(arg(0)) + ").";
            case FORECAST_UNAVAILABLE_FROST -> "The weather forecast is not available right now: frost was not checked.";
            case FORECAST_UNAVAILABLE_RISK -> "The weather forecast is not available right now: weather-driven risk was "
                    + "not checked.";
            case NO_FORECAST_DAYS -> "The weather forecast has no coming days: frost was not checked.";
            case NO_COORDINATES_FROST -> "Add your garden's coordinates to use the local forecast: frost was not checked.";
            case NO_COORDINATES_RISK -> "Add your garden's coordinates to use the local forecast: weather-driven risk "
                    + "was not checked.";
            case OUTSIDE_ITALY -> "The sowing and harvest calendars cover Italy only, and your garden is outside Italy.";
            case LIGHT_IN_PLOT -> "Not advised in " + arg(0) + ": they need more light than the plot gets ("
                    + arg(2) + "): " + arg(1) + ".";
            case LIGHT_ANYWHERE -> "Not advised now: no plot gets enough light for them (" + arg(1) + "): " + arg(0) + ".";
            case LIGHT_UNKNOWN -> "Light needs not known, not checked: " + arg(0) + ".";
            case ZONE_OF_QUESTION -> "The question names zone " + arg(0) + ", but the garden is in zone " + arg(1)
                    + ": the question's zone was used.";
            case NO_GARDEN_DEFAULTS -> "The question was asked outside a garden: zone CENTRO and the current month "
                    + "were used, and no garden's weather or plants were read.";
            case NO_GARDEN_FOR_QUESTION -> "This question is about a garden: ask it from inside one of the space's "
                    + "gardens.";
            case GARDEN_GONE -> "The garden this page was opened in no longer exists: the question was not answered.";
            case PLOT_NOT_FOUND -> "There is no plot named \"" + arg(0) + "\" in " + arg(1)
                    + ": what grows there and its rotation were not checked.";
            case PLOT_AMBIGUOUS -> "More than one plot in " + arg(1) + " matches \"" + arg(0) + "\": " + arg(2) + ".";
            case WINDOWS_FROM_CLIMATE -> "The sowing and harvest windows are those of central Italy, derived "
                    + "for the garden's climate profile (AgERA5 1996-2025).";
            case NO_CLIMATE_PROFILE -> "There is no climate profile for this garden's position: its sowing and "
                    + "harvest windows are not known.";
            case ALTITUDE_MISSING -> "The garden's altitude is not known: the frost dates were not corrected for it.";
            case ALTITUDE_COAST -> "The garden is on the coast, below its climate cell: the frost dates were not "
                    + "corrected for altitude.";
            case ALTITUDE_CAPPED -> "The garden's altitude is more than 500 m from its climate cell's: the frost "
                    + "dates were corrected by a capped 500 m.";
        };
    }

    private static String stale(String since) {
        return since.isEmpty() ? "" : ", from " + STALE.format(LocalDateTime.parse(since)) + ": the service is not reachable";
    }
}
