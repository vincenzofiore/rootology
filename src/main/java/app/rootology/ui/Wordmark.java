package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;

/**
 * The app's name with the sprout icon as its central "o": Root(sprout)logy (docs/brand). Read as "Rootology"
 * by screen readers; sized and seated by the `rootology-wordmark` rules in styles/rootology.css.
 */
class Wordmark extends Span {

    /** The slogan, the same in every language of the UI (#87). */
    static final String SLOGAN = "Ask. Know. Grow.";

    Wordmark() {
        Span root = new Span("Root");
        Image o = new Image("icons/rootology.svg", "");
        Span logy = new Span("logy");
        for (Component part : new Component[] {root, o, logy}) {
            part.getElement().setAttribute("aria-hidden", "true");
        }
        add(root, o, logy);
        addClassName("rootology-wordmark");
        getElement().setAttribute("role", "img");
        getElement().setAttribute("aria-label", "Rootology");
    }
}
