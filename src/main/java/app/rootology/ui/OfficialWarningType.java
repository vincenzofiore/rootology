package app.rootology.ui;

import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * The phenomenon of an official MeteoAlarm warning (#136), from the {@code awareness_type} of the feed: the icon
 * says what it is, its translated name is the tooltip and aria label. A type MeteoAlarm adds later keeps the
 * generic warning icon; nothing is guessed from the English event.
 */
enum OfficialWarningType {

    WIND(1, VaadinIcon.ARROWS_LONG_H, "warning.type.wind"),
    /** Snow and ice share the snowflake of the frost the app already draws. */
    SNOW_ICE(2, VaadinIcon.ASTERISK, "warning.type.snow-ice"),
    THUNDERSTORM(3, VaadinIcon.BOLT, "warning.type.thunderstorm"),
    FOG(4, VaadinIcon.CLOUD, "warning.type.fog"),
    HIGH_TEMPERATURE(5, VaadinIcon.FIRE, "warning.type.high-temperature"),
    LOW_TEMPERATURE(6, VaadinIcon.ASTERISK, "warning.type.low-temperature"),
    COASTAL_EVENT(7, VaadinIcon.ANCHOR, "warning.type.coastal-event"),
    FOREST_FIRE(8, VaadinIcon.FIRE, "warning.type.forest-fire"),
    AVALANCHES(9, VaadinIcon.ARROW_LONG_DOWN, "warning.type.avalanches"),
    RAIN(10, VaadinIcon.DROP, "warning.type.rain"),
    FLOODING(12, VaadinIcon.FILL, "warning.type.flooding"),
    RAIN_FLOOD(13, VaadinIcon.DROP, "warning.type.rain-flood"),
    OTHER(-1, VaadinIcon.WARNING, "warning.type.other");

    private final int code;
    private final VaadinIcon icon;
    private final String label;

    OfficialWarningType(int code, VaadinIcon icon, String label) {
        this.code = code;
        this.icon = icon;
        this.label = label;
    }

    VaadinIcon icon() {
        return icon;
    }

    /** The key of the type's name, the icon's tooltip and aria label. */
    String label() {
        return label;
    }

    /** The type of a warning from the feed's {@code awareness_type}; an unknown code is {@link #OTHER}. */
    static OfficialWarningType of(int type) {
        for (OfficialWarningType known : values()) {
            if (known.code == type) {
                return known;
            }
        }
        return OTHER;
    }
}
