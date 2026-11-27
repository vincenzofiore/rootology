package app.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.theme.lumo.LumoUtility.AlignItems;
import com.vaadin.flow.theme.lumo.LumoUtility.Background;
import com.vaadin.flow.theme.lumo.LumoUtility.BorderRadius;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.FlexDirection;
import com.vaadin.flow.theme.lumo.LumoUtility.FlexWrap;
import com.vaadin.flow.theme.lumo.LumoUtility.FontSize;
import com.vaadin.flow.theme.lumo.LumoUtility.FontWeight;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import com.vaadin.flow.theme.lumo.LumoUtility.Padding;
import com.vaadin.flow.theme.lumo.LumoUtility.TextColor;
import app.rootology.weather.DailyForecast;
import app.rootology.weather.ForecastHour;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The coming days of a garden's forecast (#31): a tile per day with MET Norway's icon (MIT, served from
 * /weather-icons), maximum and minimum, precipitation, and frost days highlighted. Credits MET Norway, as its
 * licence asks. It is a card of the garden home (#84).
 */
class WeatherCard extends Div {

    /** A day at or below this minimum is shown as a frost day. */
    static final double FROST = 0.0;

    /** The first day of the forecast at or below the frost threshold, if any (#84, the plots page). */
    static Optional<DailyForecast> firstFrost(List<DailyForecast> days) {
        return days.stream().filter(WeatherCard::frost).findFirst();
    }

    private static boolean frost(DailyForecast day) {
        return day.minimum() <= FROST;
    }

    /** The clock at the garden's own zone, for the day of "today" and the hours of the forecast (#158). */
    private final Clock clock;

    /** How far from now a forecast hour may be to be shown as the weather of now (as the gardens map's #67). */
    private static final java.time.Duration NOW_DISTANCE = java.time.Duration.ofMinutes(60);

    /** The forecast hour nearest to the given instant, within an hour of it: the weather of now (#132). */
    static java.util.Optional<ForecastHour> now(List<ForecastHour> hours, Instant at) {
        return hours.stream()
                .filter(h -> java.time.Duration.between(h.time(), at).abs().compareTo(NOW_DISTANCE) <= 0)
                .min(java.util.Comparator.comparing(h -> java.time.Duration.between(h.time(), at).abs()));
    }

    /**
     * @param clock the Clock bean in the garden's own zone (#158): what "today" means and the hours shown
     */
    WeatherCard(List<DailyForecast> days, java.util.Optional<ForecastHour> now, Instant fetchedAt, boolean stale,
                Clock clock) {
        addClassNames("rootology-card", "rootology-weather", Display.FLEX, FlexDirection.COLUMN, Gap.SMALL, Padding.MEDIUM,
                BorderRadius.LARGE, Background.CONTRAST_5);
        Locale locale = NoteText.locale(this);
        this.clock = clock;
        Icon cloud = VaadinIcon.CLOUD_O.create();
        Span title = new Span(getTranslation("weather.title"));
        title.addClassNames(FontWeight.SEMIBOLD);
        Div header = new Div(cloud, title);
        header.addClassNames("rootology-card-title", Display.FLEX, AlignItems.CENTER, Gap.SMALL);
        add(header);
        now.ifPresent(hour -> add(now(hour, locale)));

        // Seven days: a column-wrapped grid on a computer, one scrollable row on a phone (#88), so the
        // seventh day is never alone on a line of its own.
        Div tiles = new Div();
        tiles.addClassNames("rootology-weather-days", Display.FLEX, FlexWrap.WRAP, Gap.SMALL);
        days.forEach(day -> tiles.add(tile(day, locale)));
        add(tiles);

        Anchor credit = new Anchor("https://api.met.no/", getTranslation("weather.credit"));
        credit.setTarget("_blank");
        Div footer = new Div(credit);
        footer.addClassNames(FontSize.XSMALL, TextColor.SECONDARY);
        if (stale) {
            footer.add(new Span(" · " + getTranslation("note.stale", DateTimeFormatter.ofPattern("d MMMM HH:mm", locale)
                    .format(fetchedAt.atZone(clock.getZone())))));
        } else if (fetchedAt != null) {
            // How fresh the forecast is (#130).
            footer.add(new Span(" · " + when("weather.updated", fetchedAt)));
        }
        add(footer);
    }

    /** "aggiornato alle 17:36" today at the garden, with the date otherwise (#130, #158). */
    private String when(String key, Instant at) {
        ZoneId zone = clock.getZone();
        if (at.atZone(zone).toLocalDate().equals(java.time.LocalDate.ofInstant(clock.instant(), zone))) {
            return getTranslation(key + ".today", DateTimeFormatter.ofPattern("HH:mm").format(at.atZone(zone)));
        }
        return getTranslation(key, DateText.dateTime(at, this));
    }

    /**
     * The weather of now, in evidence above the days (#132): the symbol, the temperature, and the hour's rain
     * and humidity. It is MET Norway's forecast for this hour, not a reading, and it says so.
     */
    private Div now(ForecastHour hour, Locale locale) {
        Div now = new Div();
        now.addClassName("rootology-weather-now");
        if (hour.symbol() != null) {
            Image icon = new Image("weather-icons/" + hour.symbol() + ".svg", hour.symbol().replace('_', ' '));
            icon.addClassName("rootology-weather-now-icon");
            now.add(icon);
        }
        Span temperature = new Span(String.format(locale, "%.0f°", hour.airTemperature()));
        temperature.addClassName("rootology-weather-now-temperature");
        now.add(temperature);
        Div details = new Div();
        details.addClassName("rootology-weather-now-details");
        Span label = new Span(getTranslation("weather.now"));
        label.addClassName("rootology-weather-now-label");
        details.add(label);
        List<String> parts = new java.util.ArrayList<>();
        // An hour's rain only from a one-hour step, so a six-hour total is never read as the rain of now (#67).
        if (hour.stepHours() == 1 && hour.precipitation() != null && hour.precipitation() >= 0.1) {
            parts.add(getTranslation("weather.now.rain", hour.precipitation()));
        }
        if (hour.relativeHumidity() != null) {
            parts.add(getTranslation("weather.now.humidity", hour.relativeHumidity()));
        }
        if (!parts.isEmpty()) {
            details.add(new Span(String.join(" · ", parts)));
        }
        now.add(details);
        return now;
    }

    private Div tile(DailyForecast day, Locale locale) {
        boolean frost = frost(day);
        Span name = new Span(day.day().getDayOfWeek().getDisplayName(TextStyle.SHORT, locale) + " " + day.day().getDayOfMonth());
        name.addClassNames(FontSize.SMALL, FontWeight.MEDIUM);
        Div tile = new Div(name);
        tile.addClassNames(Display.FLEX, FlexDirection.COLUMN, AlignItems.CENTER, Gap.XSMALL, Padding.SMALL,
                BorderRadius.MEDIUM, frost ? "rootology-frost-day" : Background.BASE);
        // Its width is in the stylesheet: it grows with the card on a computer, fixed on a phone (#130).
        tile.addClassName("rootology-weather-day");
        if (day.symbol() != null) {
            Image icon = new Image("weather-icons/" + day.symbol() + ".svg", day.symbol().replace('_', ' '));
            icon.setWidth("2.5rem");
            icon.setHeight("2.5rem");
            tile.add(icon);
        }
        Span max = new Span(String.format(locale, "%.0f°", day.maximum()));
        max.addClassNames(FontWeight.SEMIBOLD);
        Span min = new Span(String.format(locale, "%.0f°", day.minimum()));
        min.addClassNames(frost ? "rootology-frost-text" : TextColor.SECONDARY);
        Div temps = new Div(max, new Span(" / "), min);
        temps.addClassNames(FontSize.SMALL);
        tile.add(temps);
        if (day.precipitation() >= 0.1) {
            Icon drop = VaadinIcon.DROP.create();
            drop.setSize("0.8em");
            Div rain = new Div(drop, new Span(String.format(locale, " %.1f mm", day.precipitation())));
            rain.addClassNames(FontSize.XSMALL, TextColor.SECONDARY, Display.FLEX, AlignItems.CENTER);
            tile.add(rain);
        }
        if (frost) {
            // The ice crystal of the climate and the winter sun (#130), not an asterisk.
            SvgIcon ice = new SvgIcon(GardenHomeView.FROST);
            ice.addClassName("rootology-frost-icon");
            Div label = new Div(ice, new Span(" " + getTranslation("weather.frost")));
            label.addClassNames(FontSize.XSMALL, "rootology-frost-text", Display.FLEX, AlignItems.CENTER);
            tile.add(label);
        }
        return tile;
    }
}
