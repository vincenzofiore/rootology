package app.rootology.ui;

/**
 * How much a weather alert of #37 matters on the garden home (#84): a frost is the graver one, a warm and wet
 * period favourable to late blight comes after it, then the rules of #137 (heat, heavy rain, strong wind and
 * the dry-days reminder). The rule is read from the event key the alert was sent with ("frost:...", "heat:..."),
 * never from its wording, which the morning check writes in Italian; the short name is a translated label.
 */
enum AlertSeverity {

    /** Cold enough to harm what grows: ice blue. */
    FROST("rootology-alert-frost", GardenHomeView.FROST, "alert.frost"),
    /** A Smith period, favourable to late blight on tomato and potato: amber. */
    BLIGHT("rootology-alert-blight", "icons/alerts/blight.svg", "alert.blight"),
    /** A maximum beyond what a growing crop stands (ECOCROP): warm red. */
    HEAT("rootology-alert-heat", GardenHomeView.THERMOMETER, "alert.heat"),
    /** At least 30 mm in a day or 10 mm in an hour: teal. */
    RAIN("rootology-alert-rain", "icons/alerts/rain.svg", "alert.rain"),
    /** A mean wind of 11 m/s or more: slate grey. */
    WIND("rootology-alert-wind", "icons/alerts/wind.svg", "alert.wind"),
    /** Three dry days and warm: earth brown, a reminder to water. */
    DRY("rootology-alert-dry", "icons/alerts/dry.svg", "alert.dry"),
    /** An alert of a rule the home does not know yet: still shown, in the neutral colour. */
    OTHER("rootology-alert-neutral", "icons/alerts/warning.svg", "alert.other");

    private final String className;
    private final String icon;
    private final String label;

    AlertSeverity(String className, String icon, String label) {
        this.className = className;
        this.icon = icon;
        this.label = label;
    }

    /** The class that colours the alert, from the stylesheet. */
    String className() {
        return className;
    }

    /** The drawing's path under the static resources (#169). */
    String icon() {
        return icon;
    }

    /** The key of the rule's short name, the icon's tooltip and aria label. */
    String label() {
        return label;
    }

    /** The severity of an alert from its event key; a key it does not know is not guessed. */
    static AlertSeverity of(String dedupKey) {
        if (dedupKey == null) {
            return OTHER;
        }
        if (dedupKey.startsWith("frost:")) {
            return FROST;
        }
        if (dedupKey.startsWith("blight:")) {
            return BLIGHT;
        }
        if (dedupKey.startsWith("heat:")) {
            return HEAT;
        }
        if (dedupKey.startsWith("rain:")) {
            return RAIN;
        }
        if (dedupKey.startsWith("wind:")) {
            return WIND;
        }
        if (dedupKey.startsWith("dry:")) {
            return DRY;
        }
        return OTHER;
    }
}
