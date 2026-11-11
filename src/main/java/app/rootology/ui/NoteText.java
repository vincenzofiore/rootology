package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.VaadinIcon;
import app.rootology.decision.Note;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A note (#31) in the page's language, with the icon and tone it is shown with. */
final class NoteText {

    /** How texts are looked up: a view's getTranslation, or the provider itself in tests. */
    interface Texts {
        String get(String key, Object... args);
    }

    /** risk: something to act on; missing: what could not be checked; info: what was used. */
    enum Tone { RISK, MISSING, INFO }

    static String text(Note note, Component in) {
        return text(note, in::getTranslation, locale(in));
    }

    static String text(Note note, Texts in, Locale locale) {
        return switch (note.kind()) {
            case FORECAST_MINIMUM -> in.get("note.FORECAST_MINIMUM", note.arg(0), day(note.arg(1), locale))
                    + stale(note.arg(2), in, locale);
            case FROST_EXCLUDED -> in.get("note.FROST_EXCLUDED", note.arg(1), note.arg(0));
            case FROST_UNKNOWN -> in.get("note.FROST_UNKNOWN", note.arg(0));
            case SMITH_PERIOD -> in.get("note.SMITH_PERIOD", day(note.arg(0), locale), note.arg(1), note.arg(2))
                    + stale(note.arg(3), in, locale);
            case NO_SMITH_PERIOD -> in.get("note.NO_SMITH_PERIOD") + stale(note.arg(0), in, locale);
            case LIGHT_IN_PLOT -> in.get("note.LIGHT_IN_PLOT", note.arg(0), note.arg(1), note.arg(2));
            case LIGHT_ANYWHERE -> in.get("note.LIGHT_ANYWHERE", note.arg(0), note.arg(1));
            case LIGHT_UNKNOWN -> in.get("note.LIGHT_UNKNOWN", note.arg(0));
            default -> in.get("note." + note.kind().name());
        };
    }

    static Tone tone(Note note) {
        return switch (note.kind()) {
            case FROST_EXCLUDED, SMITH_PERIOD, OUTSIDE_ITALY, LIGHT_IN_PLOT, LIGHT_ANYWHERE -> Tone.RISK;
            case FORECAST_MINIMUM, NO_SMITH_PERIOD, ZONE_OF_QUESTION, NO_GARDEN_DEFAULTS,
                 WINDOWS_FROM_CLIMATE, GARDEN_OF_QUESTION -> Tone.INFO;
            default -> Tone.MISSING;
        };
    }

    static VaadinIcon icon(Note note) {
        return switch (note.kind()) {
            case FORECAST_MINIMUM, FROST_EXCLUDED, FROST_UNKNOWN -> VaadinIcon.ASTERISK;
            case SMITH_PERIOD -> VaadinIcon.DROP;
            case NO_SMITH_PERIOD, LIGHT_IN_PLOT, LIGHT_ANYWHERE, LIGHT_UNKNOWN -> VaadinIcon.SUN_O;
            case OUTSIDE_ITALY -> VaadinIcon.GLOBE;
            case WINDOWS_FROM_CLIMATE, NO_CLIMATE_PROFILE, ALTITUDE_MISSING, ALTITUDE_COAST, ALTITUDE_CAPPED ->
                    VaadinIcon.SUN_O;
            case NO_COORDINATES_FROST, NO_COORDINATES_RISK -> VaadinIcon.MAP_MARKER;
            case ZONE_OF_QUESTION, NO_GARDEN_DEFAULTS -> VaadinIcon.COMPASS;
            case GARDEN_GONE, NO_GARDEN_FOR_QUESTION -> VaadinIcon.WARNING;
            case GARDEN_OF_QUESTION, GARDEN_NOT_FOUND, GARDEN_AMBIGUOUS -> VaadinIcon.HOME_O;
            case PLOT_NOT_FOUND, PLOT_AMBIGUOUS -> VaadinIcon.GRID;
            default -> VaadinIcon.CLOUD_O;
        };
    }

    static Locale locale(Component in) {
        Locale locale = in.getUI().map(com.vaadin.flow.component.UI::getLocale)
                .or(() -> java.util.Optional.ofNullable(com.vaadin.flow.component.UI.getCurrent())
                        .map(com.vaadin.flow.component.UI::getLocale))
                .orElse(Locale.ITALIAN);
        return locale.getLanguage().equals("en") ? Locale.ENGLISH : Locale.ITALIAN;
    }

    private static String day(String iso, Locale locale) {
        return DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(LocalDate.parse(iso));
    }

    private static String stale(String since, Texts in, Locale locale) {
        return since.isEmpty() ? "" : " " + in.get("note.stale",
                DateTimeFormatter.ofPattern("d MMMM HH:mm", locale).format(LocalDateTime.parse(since)));
    }

    private NoteText() {
    }
}
