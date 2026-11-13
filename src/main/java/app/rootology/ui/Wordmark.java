package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Svg;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * The app's name with the sprout in its circle as its third "o": Root(sprout)logy (docs/brand, #87). Read as
 * "Rootology" by screen readers; sized and seated by the `rootology-wordmark` rules in styles/rootology.css.
 * The sprout is inline, so it takes the accent colour of the light or dark theme.
 */
class Wordmark extends Span {

    /** The slogan, the same in every language of the UI (#87). */
    static final String SLOGAN = "Ask. Know. Grow.";

    /** The sprout in its circle, the third "o" of the name (docs/brand/rootology-o.svg), in the current colour. */
    static final String SPROUT = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 48 48\" fill=\"none\""
            + " stroke=\"currentColor\" stroke-linecap=\"round\" stroke-linejoin=\"round\""
            + " aria-hidden=\"true\" focusable=\"false\"><circle cx=\"24\" cy=\"24\" r=\"21\" stroke-width=\"3\"/>"
            + "<g stroke-width=\"3\" transform=\"translate(24 25.5) scale(1.15) translate(-24.5 -26)\">"
            + "<path d=\"M24 40 V24\"/><path d=\"M24 28 C18 27 13 23 12 17 C18 17 23 21 24 28 Z\"/>"
            + "<path d=\"M24 25 C25 18 30 13 37 12 C37 19 31 24 24 25 Z\"/></g></svg>";

    Wordmark() {
        Span root = new Span("Root");
        Svg o = new Svg(SPROUT);
        o.addClassName("rootology-wordmark-sprout");
        Span logy = new Span("logy");
        for (Component part : new Component[] {root, o, logy}) {
            part.getElement().setAttribute("aria-hidden", "true");
        }
        add(root, o, logy);
        addClassName("rootology-wordmark");
        getElement().setAttribute("role", "img");
        getElement().setAttribute("aria-label", "Rootology");
    }

    /** The name with the slogan under it, as in the bars of the app and of the public pages (#87). */
    static Div withSlogan(Component name) {
        Span slogan = new Span(SLOGAN);
        slogan.addClassName("rootology-brand-slogan");
        slogan.getElement().setAttribute("lang", "en");
        Div brand = new Div(name, slogan);
        brand.addClassName("rootology-brand");
        return brand;
    }
}
