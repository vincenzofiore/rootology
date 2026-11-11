package app.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * The coming days of a garden's forecast (#31): a tile per day with MET Norway's icon (MIT, served from
 * /weather-icons), maximum and minimum, precipitation, and frost days highlighted. Credits MET Norway, as its
 * licence asks.
 */
class WeatherCard extends Div {

    /** A day at or below this minimum is shown as a frost day. */
    static final double FROST = 0.0;

    WeatherCard(List<DailyForecast> days, Instant fetchedAt, boolean stale) {
        addClassNames(Display.FLEX, FlexDirection.COLUMN, Gap.SMALL, Padding.MEDIUM, BorderRadius.LARGE,
                Background.CONTRAST_5);
        Locale locale = NoteText.locale(this);
        Icon cloud = VaadinIcon.CLOUD_O.create();
        Span title = new Span(getTranslation("weather.title"));
        title.addClassNames(FontWeight.SEMIBOLD);
        Div header = new Div(cloud, title);
        header.addClassNames(Display.FLEX, AlignItems.CENTER, Gap.SMALL);
        add(header);

        Div tiles = new Div();
        tiles.addClassNames(Display.FLEX, FlexWrap.WRAP, Gap.SMALL);
        days.forEach(day -> tiles.add(tile(day, locale)));
        add(tiles);

        Anchor credit = new Anchor("https://api.met.no/", getTranslation("weather.credit"));
        credit.setTarget("_blank");
        Div footer = new Div(credit);
        footer.addClassNames(FontSize.XSMALL, TextColor.SECONDARY);
        if (stale) {
            footer.add(new Span(" · " + getTranslation("note.stale", DateTimeFormatter.ofPattern("d MMMM HH:mm", locale)
                    .format(fetchedAt.atZone(ZoneId.of("Europe/Rome"))))));
        }
        add(footer);
    }

    private Div tile(DailyForecast day, Locale locale) {
        boolean frost = day.minimum() <= FROST;
        Span name = new Span(day.day().getDayOfWeek().getDisplayName(TextStyle.SHORT, locale) + " " + day.day().getDayOfMonth());
        name.addClassNames(FontSize.SMALL, FontWeight.MEDIUM);
        Div tile = new Div(name);
        tile.addClassNames(Display.FLEX, FlexDirection.COLUMN, AlignItems.CENTER, Gap.XSMALL, Padding.SMALL,
                BorderRadius.MEDIUM, frost ? Background.ERROR_10 : Background.BASE);
        tile.setWidth("5.5rem");
        if (day.symbol() != null) {
            Image icon = new Image("weather-icons/" + day.symbol() + ".svg", day.symbol().replace('_', ' '));
            icon.setWidth("2.5rem");
            icon.setHeight("2.5rem");
            tile.add(icon);
        }
        Span max = new Span(String.format(locale, "%.0f°", day.maximum()));
        max.addClassNames(FontWeight.SEMIBOLD);
        Span min = new Span(String.format(locale, "%.0f°", day.minimum()));
        min.addClassNames(frost ? TextColor.ERROR : TextColor.SECONDARY);
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
            Icon ice = VaadinIcon.ASTERISK.create();
            ice.setSize("0.8em");
            Div label = new Div(ice, new Span(" " + getTranslation("weather.frost")));
            label.addClassNames(FontSize.XSMALL, TextColor.ERROR, Display.FLEX, AlignItems.CENTER);
            tile.add(label);
        }
        return tile;
    }
}
