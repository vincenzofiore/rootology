package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;

/**
 * The short messages at the bottom of the screen (#87), one pattern everywhere: an icon and a tone say what
 * kind of message it is (done, a problem, for information), then the text, and an Undo when the action can be
 * taken back. Problems stay longer, since they ask the person to do something.
 */
final class Toasts {

    enum Kind {
        DONE(VaadinIcon.CHECK_CIRCLE, 3000),
        PROBLEM(VaadinIcon.WARNING, 6000),
        INFO(VaadinIcon.INFO_CIRCLE, 4000);

        private final VaadinIcon icon;
        private final int millis;

        Kind(VaadinIcon icon, int millis) {
            this.icon = icon;
            this.millis = millis;
        }
    }

    static Notification done(String text) {
        return open(Kind.DONE, text, null, null);
    }

    static Notification problem(String text) {
        return open(Kind.PROBLEM, text, null, null);
    }

    static Notification info(String text) {
        return open(Kind.INFO, text, null, null);
    }

    /** Done, with a button that takes it back while the message is shown (twice as long, to leave time). */
    static Notification withUndo(String text, String undoLabel, Runnable undo) {
        return open(Kind.DONE, text, undoLabel, undo);
    }

    private static Notification open(Kind kind, String text, String undoLabel, Runnable undo) {
        Notification toast = new Notification();
        toast.setPosition(Notification.Position.BOTTOM_CENTER);
        toast.setDuration(undo == null ? kind.millis : kind.millis * 2);
        toast.addClassName("rootology-toast");
        toast.addClassName("rootology-toast-" + kind.name().toLowerCase(java.util.Locale.ROOT));
        var icon = kind.icon.create();
        icon.addClassName("rootology-toast-icon");
        icon.getElement().setAttribute("aria-hidden", "true");
        Div row = new Div(icon, new Span(text));
        row.addClassName("rootology-toast-row");
        if (undo != null) {
            Button button = new Button(undoLabel, e -> {
                toast.close();
                undo.run();
            });
            button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            button.addClassName("rootology-toast-undo");
            row.add(button);
        }
        toast.add(row);
        toast.open();
        return toast;
    }

    private Toasts() {
    }
}
