package app.rootology.ui;

import com.vaadin.flow.component.Component;
import app.rootology.pipeline.ContextSnapshot;

import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What a calendar answer means for the month it was asked in (#116), computed from its values and never from
 * Claude’s sentence: "Sì, adesso: trapianto" when that month is one of them, otherwise "Non adesso · da febbraio:
 * semina in semenzaio", the first of them after it. The month is the question’s, or the one it was asked in,
 * so a past answer keeps the verdict it had. Only an answer whose values are all months has one.
 */
final class CalendarVerdict {

    private static final ZoneId ITALY = ZoneId.of("Europe/Rome");

    /**
     * Whether the month of the question is one of the answer's months; the month the verdict names; the words
     * with how the crop goes into the ground, and the brief words without it, for a history card (#122).
     */
    record Verdict(boolean now, Month month, String words, String brief) {
    }

    /**
     * @param askedAt when the question was asked: its month, in Italy, when the context carries none (the
     *                calendar questions store no month, #116)
     */
    static Optional<Verdict> of(List<String> values, Map<String, List<String>> activities, ContextSnapshot context,
                                Instant askedAt, Component in) {
        if (values.isEmpty() || !values.stream().allMatch(CalendarVerdict::isMonth)) {
            return Optional.empty();
        }
        boolean named = context != null && context.month() != null;
        Month asked = named ? Month.valueOf(context.month()) : askedAt.atZone(ITALY).getMonth();
        boolean question = named && context.monthFrom() == ContextSnapshot.MonthFrom.QUESTION;
        String askedName = asked.getDisplayName(TextStyle.FULL, NoteText.locale(in));
        if (values.contains(asked.name())) {
            String doing = doing(asked, activities, in);
            String key = question ? "verdict.month.yes" : "verdict.now.yes";
            return Optional.of(new Verdict(true, asked,
                    in.getTranslation(key + (doing.isEmpty() ? "" : ".doing"), askedName, doing),
                    in.getTranslation(key, askedName, doing)));
        }
        Month next = asked;
        do {
            next = next.plus(1);
        } while (!values.contains(next.name()));
        String doing = doing(next, activities, in);
        String key = question ? "verdict.month.no" : "verdict.now.no";
        String nextName = next.getDisplayName(TextStyle.FULL, NoteText.locale(in));
        return Optional.of(new Verdict(false, next,
                in.getTranslation(key + (doing.isEmpty() ? "" : ".doing"), askedName, nextName, doing),
                in.getTranslation(key, askedName, nextName, doing)));
    }

    /** How the month's crop goes into the ground, in words ("semina in semenzaio"), empty when unknown. */
    private static String doing(Month month, Map<String, List<String>> activities, Component in) {
        return activities.getOrDefault(month.name(), List.of()).stream()
                .map(a -> in.getTranslation("verdict.activity." + a))
                .collect(Collectors.joining(in.getTranslation("verdict.and")));
    }

    private static boolean isMonth(String value) {
        try {
            Month.valueOf(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private CalendarVerdict() {
    }
}
