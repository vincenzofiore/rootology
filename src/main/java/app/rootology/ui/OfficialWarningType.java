package app.rootology.ui;

/**
 * The phenomenon of an official MeteoAlarm warning (#136), from the {@code awareness_type} of the feed: the icon
 * says what it is, its translated name is the tooltip and aria label. A type MeteoAlarm adds later keeps the
 * generic warning triangle; nothing is guessed from the English event.
 */
enum OfficialWarningType {

    WIND(1, "icons/alerts/wind.svg", "warning.type.wind"),
    SNOW_ICE(2, "icons/alerts/snow-ice.svg", "warning.type.snow-ice"),
    THUNDERSTORM(3, "icons/alerts/thunderstorm.svg", "warning.type.thunderstorm"),
    FOG(4, "icons/alerts/fog.svg", "warning.type.fog"),
    HIGH_TEMPERATURE(5, GardenHomeView.THERMOMETER, "warning.type.high-temperature"),
    /** The snowflake of the frost the app already draws. */
    LOW_TEMPERATURE(6, GardenHomeView.FROST, "warning.type.low-temperature"),
    COASTAL_EVENT(7, "icons/alerts/coastal-event.svg", "warning.type.coastal-event"),
    FOREST_FIRE(8, "icons/alerts/forest-fire.svg", "warning.type.forest-fire"),
    AVALANCHES(9, "icons/alerts/avalanches.svg", "warning.type.avalanches"),
    /** Heavy rain, the drawing of the rain alert of #137. */
    RAIN(10, "icons/alerts/rain.svg", "warning.type.rain"),
    FLOODING(12, "icons/alerts/flooding.svg", "warning.type.flooding"),
    RAIN_FLOOD(13, "icons/alerts/rain.svg", "warning.type.rain-flood"),
    OTHER(-1, "icons/alerts/warning.svg", "warning.type.other");

    private final int code;
    private final String icon;
    private final String label;

    OfficialWarningType(int code, String icon, String label) {
        this.code = code;
        this.icon = icon;
        this.label = label;
    }

    /** The drawing's path under the static resources (#169). */
    String icon() {
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
