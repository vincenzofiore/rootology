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
        OUTSIDE_ITALY
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
        };
    }

    private static String stale(String since) {
        return since.isEmpty() ? "" : ", from " + STALE.format(LocalDateTime.parse(since)) + ": the service is not reachable";
    }
}
