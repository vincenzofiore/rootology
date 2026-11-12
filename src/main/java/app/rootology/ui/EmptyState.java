package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * What a page shows instead of an empty grid (#81): one icon, one short sentence and, when there is
 * something to do about it, one primary button. The drawing comes with #87, so the icon is chosen here.
 */
public class EmptyState extends Div {

    /** The one place to change the drawing (#87). */
    static final VaadinIcon ICON = VaadinIcon.INBOX;

    /** The action, when there is one: a guest only looks, so the caller hides it (#33). */
    private Button action;

    /** A sentence alone: nothing to do, or the person may only look (#33). */
    public EmptyState(String text) {
        this(ICON, text, null, null);
    }

    /** A sentence and the action that fills the page. */
    public EmptyState(String text, String buttonText, Runnable action) {
        this(ICON, text, buttonText, action);
    }

    EmptyState(VaadinIcon icon, String text, String buttonText, Runnable action) {
        addClassName("rootology-empty");
        Icon drawing = icon.create();
        drawing.addClassName("rootology-empty-icon");
        drawing.getElement().setAttribute("aria-hidden", "true");
        Paragraph sentence = new Paragraph(text);
        sentence.addClassName("rootology-empty-text");
        add(drawing, sentence);
        if (buttonText != null && action != null) {
            this.action = new Button(buttonText, e -> action.run());
            this.action.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            add(this.action);
        }
    }

    /** Shows or hides the action: a guest sees the sentence alone (#33). */
    public EmptyState actionVisible(boolean visible) {
        if (action != null) {
            action.setVisible(visible);
        }
        return this;
    }
}
