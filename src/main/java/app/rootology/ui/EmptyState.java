package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.SvgIcon;

/**
 * What a page shows instead of an empty grid (#81): one drawing, one short sentence and, when there is
 * something to do about it, one primary button. The drawings are Rootology's own, in the hand-drawn line
 * style of #87 (`icons/empty/`), in the soil's colour.
 */
public class EmptyState extends Div {

    /** The drawings an empty page can show (#87). */
    public enum Drawing {
        /** Nothing grows here yet: a garden, an area, a planting to add. */
        SPROUT("sprout"),
        /** Nothing written yet: questions asked, facts proposed. */
        NOTEBOOK("notebook"),
        /** Nothing to be told. */
        BELL("bell"),
        /** Nothing left to do: no question waits for a person. */
        WATERING_CAN("watering-can");

        private final String file;

        Drawing(String file) {
            this.file = file;
        }

        String src() {
            return "icons/empty/" + file + ".svg";
        }
    }

    private final SvgIcon drawing = new SvgIcon(Drawing.SPROUT.src());

    /** The action, when there is one: a guest only looks, so the caller hides it (#33). */
    private Button action;

    /** A sentence alone: nothing to do, or the person may only look (#33). */
    public EmptyState(String text) {
        this(text, null, null);
    }

    /** A sentence and the action that fills the page. */
    public EmptyState(String text, String buttonText, Runnable action) {
        addClassName("rootology-empty");
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

    /** Another drawing than the sprout, for what the page is about. */
    public EmptyState drawing(Drawing kind) {
        drawing.setSrc(kind.src());
        return this;
    }

    /** The drawing's path, for tests. */
    String drawingSrc() {
        return drawing.getSrc();
    }

    /** Shows or hides the action: a guest sees the sentence alone (#33). */
    public EmptyState actionVisible(boolean visible) {
        if (action != null) {
            action.setVisible(visible);
        }
        return this;
    }
}
