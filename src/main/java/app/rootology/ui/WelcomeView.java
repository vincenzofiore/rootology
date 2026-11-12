package app.rootology.ui;

import com.vaadin.flow.component.Svg;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;

import java.util.Map;

/**
 * The public landing page (#47): what Rootology does, drawn as Ask, Know, Grow and four features (#87). The way
 * in, and for whoever has no access yet the way to ask for an invitation (#65), is Sign in in the bar (SignInMenu). A visitor who is not signed in
 * arrives here instead of straight on GitHub's sign-in; someone already signed in is sent to Home.
 * A refused sign-in comes back with {@code error=denied} and one generic message, never the provider's.
 */
@Route(value = WelcomeView.ROUTE, layout = PublicLayout.class)
@AnonymousAllowed
public class WelcomeView extends Div implements BeforeEnterObserver, HasDynamicTitle {

    static final String ROUTE = "welcome";
    /** The query parameter logout adds: the page then says so, with the hint about the provider's own sign-in. */
    static final String SIGNED_OUT = "signed-out";

    private final AuthenticationContext authentication;
    private final Paragraph denied = new Paragraph();
    private final Paragraph signedOut = new Paragraph();

    public WelcomeView(AuthenticationContext authentication) {
        this.authentication = authentication;
        addClassName("rootology-prose");
        addClassName("rootology-welcome");

        // A refused sign-in or a logout is said first (#47, #87). The name and the slogan are in the bar
        // (PublicLayout); the page opens with how the app works.
        denied.addClassName("rootology-welcome-denied");
        denied.setVisible(false);
        signedOut.addClassName("rootology-welcome-signed-out");
        signedOut.setVisible(false);
        add(denied, signedOut);
        add(new H2(getTranslation("welcome.title")), cycle(), new Paragraph(getTranslation("welcome.lead")));

        // What the app does, each feature drawn in the brand's line style (#87).
        Div features = new Div(feature("sources"), feature("unsure"), feature("warns"), feature("spaces"));
        features.addClassName("rootology-features");
        add(features);

        // Sources and Privacy are in the line at the bottom of PublicLayout.
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // A person waiting for an invitation (#65) may come back here: Home would only send them to their request.
        if (authentication.isAuthenticated() && !PendingAccess.isPending()) {
            event.rerouteTo(StartView.class);
            return;
        }
        boolean refused = event.getLocation().getQueryParameters().getSingleParameter("error").isPresent();
        denied.setText(refused ? getTranslation("welcome.denied") : "");
        denied.setVisible(refused);
        boolean left = event.getLocation().getQueryParameters().getParameters().containsKey(SIGNED_OUT);
        signedOut.setText(left ? getTranslation("loggedout.title") + ". " + getTranslation("loggedout.github") : "");
        signedOut.setVisible(left);
    }

    /** Ask, Know, Grow in a circle (#87): a person asks, the AI checks the facts, the garden grows. */
    private Svg cycle() {
        Svg drawing = BrandDrawing.of("/brand/how-it-works.svg", Map.of("label", getTranslation("welcome.cycle")));
        drawing.addClassName("rootology-welcome-cycle");
        return drawing;
    }

    /**
     * One feature of the welcome page: its drawing (brand/features/{name}.svg, wordless so it serves every
     * language, described to screen readers in theirs), its title and one sentence.
     */
    private Div feature(String name) {
        Svg drawing = BrandDrawing.of("/brand/features/" + name + ".svg",
                Map.of("label", getTranslation("welcome." + name + ".drawing")));
        drawing.addClassName("rootology-feature-drawing");
        Div feature = new Div(drawing, new H3(getTranslation("welcome." + name + ".title")),
                new Paragraph(getTranslation("welcome." + name + ".text")));
        feature.addClassName("rootology-feature");
        return feature;
    }

    @Override
    public String getPageTitle() {
        return getTranslation("welcome.page") + " · Rootology";
    }
}
