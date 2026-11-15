package app.rootology.ui;

import com.vaadin.flow.component.Component;
import app.rootology.pipeline.ContextSnapshot;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The context a question was answered in (#55) in the page's language: the space and the garden, the plot
 * when the question named one, the zone and where it came from, the month and where it came from when the
 * question named it. Example: "Risposta per: Casa · Orto sul tetto · Aiuola B · zona NORD (dall’orto) · novembre".
 */
final class ContextText {

    static String line(ContextSnapshot context, Component in) {
        return line(context, in::getTranslation, NoteText.locale(in));
    }

    static String line(ContextSnapshot context, NoteText.Texts in, Locale locale) {
        List<String> parts = new ArrayList<>();
        parts.add(context.hasPlace()
                ? in.get("context.place.place", name(context.spaceName()), context.placeName())
                : context.hasGarden()
                ? in.get("context.place.garden", name(context.spaceName()), context.gardenName())
                : in.get("context.place.nogarden", name(context.spaceName())));
        if (context.plotName() != null) {
            parts.add(context.plotName());
        }
        parts.addAll(climate(context, in, locale));
        if (context.month() != null) {
            String month = Month.valueOf(context.month()).getDisplayName(TextStyle.FULL, locale);
            parts.add(context.monthFrom() == ContextSnapshot.MonthFrom.QUESTION
                    ? in.get("context.month.question", month) : month);
        }
        return in.get("context.for", String.join(" · ", parts));
    }

    /**
     * The same line without the space and the garden, for a card that already says which garden it belongs
     * to (#109); empty when there is nothing else to say.
     */
    static String rest(ContextSnapshot context, Component in) {
        return rest(context, in::getTranslation, NoteText.locale(in));
    }

    static String rest(ContextSnapshot context, NoteText.Texts in, Locale locale) {
        List<String> parts = new ArrayList<>();
        if (context.plotName() != null) {
            parts.add(context.plotName());
        }
        parts.addAll(climate(context, in, locale));
        if (context.month() != null) {
            String month = Month.valueOf(context.month()).getDisplayName(TextStyle.FULL, locale);
            parts.add(context.monthFrom() == ContextSnapshot.MonthFrom.QUESTION
                    ? in.get("context.month.question", month) : month);
        }
        return String.join(" · ", parts);
    }

    /**
     * What the short line above an answer does not say (#111), for the fold of an answer card: the climate the
     * calendars were read from with its source, a zone from a row stored before #62, and a month the question
     * named. Empty when the brief line says everything.
     */
    static String detail(ContextSnapshot context, Component in) {
        return detail(context, in::getTranslation, NoteText.locale(in));
    }

    static String detail(ContextSnapshot context, NoteText.Texts in, Locale locale) {
        List<String> parts = new ArrayList<>(climate(context, in, locale));
        if (context.month() != null && context.monthFrom() == ContextSnapshot.MonthFrom.QUESTION) {
            parts.add(in.get("context.month.question",
                    Month.valueOf(context.month()).getDisplayName(TextStyle.FULL, locale)));
        }
        return String.join(" · ", parts);
    }

    /** The short line above an answer (#83): the garden (or the space without one), the area, the month. */
    static String brief(ContextSnapshot context, Component in) {
        return brief(context, in::getTranslation, NoteText.locale(in));
    }

    static String brief(ContextSnapshot context, NoteText.Texts in, Locale locale) {
        List<String> parts = new ArrayList<>();
        parts.add(context.hasPlace() ? context.placeName()
                : context.hasGarden() ? context.gardenName()
                : in.get("context.place.nogarden", name(context.spaceName())));
        if (context.plotName() != null) {
            parts.add(context.plotName());
        }
        if (context.month() != null) {
            parts.add(Month.valueOf(context.month()).getDisplayName(TextStyle.FULL, locale));
        }
        return String.join(" · ", parts);
    }

    /**
     * The climate of the question's site with its source (#62), or else the zone of a row stored before it;
     * nothing at all when the context knows neither.
     */
    private static List<String> climate(ContextSnapshot context, NoteText.Texts in, Locale locale) {
        if (context.hasClimate()) {
            return List.of(context.lastFrostDay() == null ? in.get("context.climate.none")
                    : in.get("context.climate." + context.climateFrom().name().toLowerCase(Locale.ROOT),
                    ClimateText.frost(context.lastFrostDay(), in, locale)));
        }
        if (context.zone() != null) {
            return List.of(in.get(switch (context.zoneFrom()) {
                case QUESTION -> "context.zone.question";
                case GARDEN -> "context.zone.garden";
                case DEFAULT -> "context.zone.default";
            }, context.zone()));
        }
        return List.of();
    }

    private static String name(String spaceName) {
        return spaceName == null ? "" : spaceName;
    }

    private ContextText() {
    }
}
