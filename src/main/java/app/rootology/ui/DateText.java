package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;

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

    /** The days and the hours shown are Italian ones, as everywhere in the app (#19, #37). */
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

    static String date(LocalDate day, Component in) {
        return date(day, LocalDate.now(ITALY), NoteText.locale(in));
    }

    static String relative(LocalDate day, Component in) {
        return relative(day, LocalDate.now(ITALY), in::getTranslation);
    }

    static String dateTime(Instant at, Component in) {
        return dateTime(at, LocalDate.now(ITALY), ITALY, NoteText.locale(in));
    }

    /** A short date that shows its full form on hover, for the places that take a component (a grid cell). */
    static Span dateSpan(LocalDate day, Component in) {
        Span span = new Span(date(day, in));
        span.setTitle(fullDate(day, NoteText.locale(in)));
        return span;
    }

    /** A relative date ("12 giorni fa") that shows the full date on hover. */
    static Span relativeSpan(LocalDate day, Component in) {
        Span span = new Span(relative(day, in));
        span.setTitle(fullDate(day, NoteText.locale(in)));
        return span;
    }

    /** A short date and time that show their full form on hover. */
    static Span dateTimeSpan(Instant at, Component in) {
        Span span = new Span(dateTime(at, in));
        span.setTitle(fullDateTime(at, ITALY, NoteText.locale(in)));
        return span;
    }

    private DateText() {
    }
}
