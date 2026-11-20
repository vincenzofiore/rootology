package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.ClimateWindows;
import app.rootology.facts.ClimateWindows.ClimateWindow;
import app.rootology.facts.FrostSeasonRule;
import app.rootology.facts.ReferenceSite;
import app.rootology.facts.SiteProfile;
import app.rootology.garden.GardenViews.GardenSummary;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * When a crop growing in a garden is usually harvested (#131): the HARVEST windows of the garden's own site,
 * as {@link ClimateWindows} gives them, never a date estimated from the crop's cycle. The value on the page is
 * the months ("lug–set"); the whole sentence is the tooltip and what a screen reader says. No window, nothing.
 */
final class HarvestText {

    static final String ACTIVITY = "HARVEST";
    /** The hand-drawn basket of the home's icons (#130), the same in the chips of both pages. */
    static final String ICON = "icons/home/harvest.svg";
    /** The dash of a month range, as in the app's other texts ("apr–set"). */
    static final String DASH = "–";

    /**
     * The site whose calendars this garden's questions use: its own cell and altitude, or the default of the
     * questions (Vasto) when it has no coordinates. Empty for a garden abroad with no coordinates.
     */
    static Optional<SiteProfile> site(GardenSummary garden, ClimateCells cells) {
        if (garden == null) {
            return Optional.empty();
        }
        if (garden.latitude() != null && garden.longitude() != null) {
            return cells.nearest(garden.latitude().doubleValue(), garden.longitude().doubleValue())
                    .map(cell -> FrostSeasonRule.profile(cell, garden.elevationM(), null));
        }
        if (garden.countryCode() != null && !garden.countryCode().equals("IT")) {
            return Optional.empty();
        }
        return Optional.ofNullable(ReferenceSite.CENTRO.profile(cells));
    }

    /** The harvest windows of every crop of a site, by crop id, in calendar order (a crop may have two). */
    static Map<String, List<ClimateWindow>> harvest(SiteProfile site, ClimateWindows windows) {
        if (site == null || windows == null) {
            return Map.of();
        }
        return windows.windows(site).stream()
                .filter(window -> ACTIVITY.equals(window.window().activity()))
                .sorted(Comparator.comparingInt(window -> window.window().fromMonth()))
                .collect(Collectors.groupingBy(window -> window.window().cropId(), LinkedHashMap::new,
                        Collectors.toList()));
    }

    /** "lug–set"; several windows joined ("apr–mag, set–ott"); empty when the crop has no window. */
    static String months(List<ClimateWindow> windows, Locale locale) {
        return ranges(windows, locale, TextStyle.SHORT);
    }

    /** The same in the sentence's words: "luglio–settembre". */
    static String fullMonths(List<ClimateWindow> windows, Locale locale) {
        return ranges(windows, locale, TextStyle.FULL);
    }

    private static String ranges(List<ClimateWindow> windows, Locale locale, TextStyle style) {
        if (windows == null) {
            return "";
        }
        return windows.stream().map(window -> range(window, locale, style)).collect(Collectors.joining(", "));
    }

    /** One window; a window over the new year keeps its two months as they are ("ott–feb"). */
    private static String range(ClimateWindow window, Locale locale, TextStyle style) {
        int from = window.window().fromMonth();
        int to = window.window().toMonth();
        String first = month(from, style, locale);
        return from == to ? first : first + DASH + month(to, style, locale);
    }

    /** A window of twelve months, such as 1–12 or 3–2: the whole year, said as such rather than "gen–dic". */
    static boolean allYear(ClimateWindow window) {
        return window.window().toMonth() % 12 + 1 == window.window().fromMonth();
    }

    private static String month(int month, TextStyle style, Locale locale) {
        return Month.of(month).getDisplayName(style, locale);
    }

    /**
     * The value of #131 on a crop's chip: the harvest icon with the months, the sentence as tooltip and aria
     * label; empty when the crop has no harvest window there, so nothing is guessed.
     */
    static Optional<Component> value(List<ClimateWindow> windows, NoteText.Texts in, Locale locale) {
        if (windows == null || windows.isEmpty()) {
            return Optional.empty();
        }
        boolean allYear = windows.stream().anyMatch(HarvestText::allYear);
        String said = allYear ? in.get("harvest.all.year") : in.get("harvest.months", fullMonths(windows, locale));
        SvgIcon icon = new SvgIcon(ICON);
        icon.addClassName("rootology-harvest-icon");
        icon.getElement().setAttribute("aria-hidden", "true");
        Span months = new Span(allYear ? in.get("harvest.all.year.short") : months(windows, locale));
        months.addClassName("rootology-harvest-months");
        months.getElement().setAttribute("title", said);
        months.getElement().setAttribute("aria-label", said);
        Span value = new Span(icon, months);
        value.addClassName("rootology-grow-harvest");
        return Optional.of(value);
    }

    private HarvestText() {
    }
}
