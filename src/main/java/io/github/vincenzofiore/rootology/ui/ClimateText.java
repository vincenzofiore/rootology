package io.github.vincenzofiore.rootology.ui;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * A frost date (#62) in the page's language, rounded the way the owner asked: the first ten days are
 * "inizio", the next ten "metà", the rest "fine", followed by the month. Example: "fine marzo".
 */
final class ClimateText {

    static String frost(Integer dayOfYear, NoteText.Texts in, Locale locale) {
        LocalDate date = LocalDate.of(2025, 1, 1).plusDays(dayOfYear - 1L);
        String month = date.getMonth().getDisplayName(TextStyle.FULL, locale);
        int day = date.getDayOfMonth();
        String part = day <= 10 ? "climate.start" : day <= 20 ? "climate.middle" : "climate.end";
        return in.get(part, month);
    }

    private ClimateText() {
    }
}
