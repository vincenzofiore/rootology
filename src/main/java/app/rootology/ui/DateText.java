package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * A date in the page's language (#79): short and localized ("3 mag" / "3 May", with the year only when it is
 * not the current one), or relative to today ("12 giorni fa"), with the full date for a tooltip. Never a
 * {@code LocalDate.toString()}: "2026-05-03" is not a date a person reads.
 */
final class DateText {

    /**
     * Italian time: what a date about the app or a person (account, members, invitations, curator, admin) is
     * shown in. A date about a garden is shown in the garden's own zone (#158).
     */
    static final ZoneId ITALY = ZoneId.of("Europe/Rome");

    /** "3 mag", "3 mag 2025": the year is told only when it is not the current one. */
    static String date(LocalDate day, LocalDate today, Locale locale) {
        String pattern = day.getYear() == today.getYear() ? "d MMM" : "d MMM yyyy";
        return DateTimeFormatter.ofPattern(pattern, locale).format(day);
    }

    /** "oggi", "ieri", "12 giorni fa", "tra 3 giorni". */
    static String relative(LocalDate day, LocalDate today, NoteText.Texts in) {
        long days = ChronoUnit.DAYS.between(day, today);
        if (days == 0) {
            return in.get("date.today");
        }
        if (days == 1) {
            return in.get("date.yesterday");
        }
        if (days == -1) {
            return in.get("date.tomorrow");
        }
        return days > 0 ? in.get("date.daysago", days) : in.get("date.indays", -days);
    }

    /** "3 mag, 14:05": the day with the hour, the year only when it is not the current one. */
    static String dateTime(Instant at, LocalDate today, ZoneId zone, Locale locale) {
        java.time.ZonedDateTime when = at.atZone(zone);
        String pattern = when.getYear() == today.getYear() ? "d MMM, HH:mm" : "d MMM yyyy, HH:mm";
        return DateTimeFormatter.ofPattern(pattern, locale).format(when);
    }

    /** The whole date, for a tooltip: "3 maggio 2026". */
    static String fullDate(LocalDate day, Locale locale) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(day);
    }

    /** The whole date with the hour, for a tooltip: "3 maggio 2026, 14:05". */
    static String fullDateTime(Instant at, ZoneId zone, Locale locale) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale).format(at.atZone(zone));
    }

    /** A date with no garden at hand: the app's own, Italian time (#19, #37). */
    static String date(LocalDate day, Component in) {
        return date(day, LocalDate.now(ITALY), NoteText.locale(in));
    }

    /** A date about a garden: the clock at the garden's zone gives "today" (#158). */
    static String date(LocalDate day, Clock clock, Component in) {
        return date(day, LocalDate.ofInstant(clock.instant(), clock.getZone()), NoteText.locale(in));
    }

    /** A date with no garden at hand: the app's own, Italian time (#19, #37). */
    static String relative(LocalDate day, Component in) {
        return relative(day, LocalDate.now(ITALY), in::getTranslation);
    }

    /** A relative date about a garden: the clock at the garden's zone gives "today" (#158). */
    static String relative(LocalDate day, Clock clock, Component in) {
        return relative(day, LocalDate.ofInstant(clock.instant(), clock.getZone()), in::getTranslation);
    }

    /** An hour with no garden at hand: the app's own, Italian time (#19, #37). */
    static String dateTime(Instant at, Component in) {
        return dateTime(at, LocalDate.now(ITALY), ITALY, NoteText.locale(in));
    }

    /** An hour about a garden, in the garden's own zone (#158). */
    static String dateTime(Instant at, Clock clock, Component in) {
        return dateTime(at, LocalDate.ofInstant(clock.instant(), clock.getZone()), clock.getZone(),
                NoteText.locale(in));
    }

    /** A short date that shows its full form on hover, for the places that take a component (a grid cell). */
    static Span dateSpan(LocalDate day, Component in) {
        Span span = new Span(date(day, in));
        span.setTitle(fullDate(day, NoteText.locale(in)));
        return span;
    }

    /** A short date about a garden (#158), its full form on hover. */
    static Span dateSpan(LocalDate day, Clock clock, Component in) {
        Span span = new Span(date(day, clock, in));
        span.setTitle(fullDate(day, NoteText.locale(in)));
        return span;
    }

    /** A relative date ("12 giorni fa") that shows the full date on hover. */
    static Span relativeSpan(LocalDate day, Component in) {
        Span span = new Span(relative(day, in));
        span.setTitle(fullDate(day, NoteText.locale(in)));
        return span;
    }

    /** A relative date about a garden (#158), its full date on hover. */
    static Span relativeSpan(LocalDate day, Clock clock, Component in) {
        Span span = new Span(relative(day, clock, in));
        span.setTitle(fullDate(day, NoteText.locale(in)));
        return span;
    }

    /** A short date and time that show their full form on hover. */
    static Span dateTimeSpan(Instant at, Component in) {
        Span span = new Span(dateTime(at, in));
        span.setTitle(fullDateTime(at, ITALY, NoteText.locale(in)));
        return span;
    }

    /** A short date and time about a garden (#158), their full form on hover. */
    static Span dateTimeSpan(Instant at, Clock clock, Component in) {
        Span span = new Span(dateTime(at, clock, in));
        span.setTitle(fullDateTime(at, clock.getZone(), NoteText.locale(in)));
        return span;
    }

    private DateText() {
    }
}
