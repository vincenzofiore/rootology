package app.rootology.ui;

import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * How much a weather alert of #37 matters on the garden home (#84): a frost is the graver one, a warm and wet
 * period favourable to late blight comes after it. The rule is read from the event key the alert was sent
 * with ("frost:...", "blight:..."), never from its wording, which the morning check writes in Italian.
 */
enum AlertSeverity {

    /** Cold enough to harm what grows: red. */
    FROST("rootology-alert-frost", VaadinIcon.ASTERISK),
    /** A Smith period, favourable to late blight on tomato and potato: amber. */
    BLIGHT("rootology-alert-blight", VaadinIcon.CLOUD_O),
    /** An alert of a rule the home does not know yet: still shown, in the neutral colour. */
    OTHER("rootology-alert-neutral", VaadinIcon.WARNING);

    private final String className;
    private final VaadinIcon icon;

    AlertSeverity(String className, VaadinIcon icon) {
        this.className = className;
        this.icon = icon;
    }

    /** The class that colours the alert, from the stylesheet. */
    String className() {
        return className;
    }

    VaadinIcon icon() {
        return icon;
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
        return OTHER;
    }
}
