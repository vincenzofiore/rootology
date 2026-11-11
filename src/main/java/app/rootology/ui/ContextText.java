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
        parts.add(context.hasGarden()
                ? in.get("context.place.garden", name(context.spaceName()), context.gardenName())
                : in.get("context.place.nogarden", name(context.spaceName())));
        if (context.plotName() != null) {
            parts.add(context.plotName());
        }
        if (context.hasClimate()) {
            // The calendar's own context (#62): the last frost the windows were derived from.
            parts.add(context.lastFrostDay() == null ? in.get("context.climate.none")
                    : in.get("context.climate." + context.climateFrom().name().toLowerCase(Locale.ROOT),
                    ClimateText.frost(context.lastFrostDay(), in, locale)));
        } else if (context.zone() != null) {
            // Rows stored before #62 still show the zone they used.
            parts.add(in.get(switch (context.zoneFrom()) {
                case QUESTION -> "context.zone.question";
                case GARDEN -> "context.zone.garden";
                case DEFAULT -> "context.zone.default";
            }, context.zone()));
        }
        if (context.month() != null) {
            String month = Month.valueOf(context.month()).getDisplayName(TextStyle.FULL, locale);
            parts.add(context.monthFrom() == ContextSnapshot.MonthFrom.QUESTION
                    ? in.get("context.month.question", month) : month);
        }
        return in.get("context.for", String.join(" · ", parts));
    }

    private static String name(String spaceName) {
        return spaceName == null ? "" : spaceName;
    }

    private ContextText() {
    }
}
