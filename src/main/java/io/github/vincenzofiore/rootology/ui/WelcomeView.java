package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.beans.factory.annotation.Value;

/**
 * The public landing page (#47): what Rootology does and how to get in. A visitor who is not signed in
 * arrives here instead of straight on GitHub's sign-in; someone already signed in is sent to Home.
 * A refused sign-in comes back with {@code error=denied} and one generic message, never the provider's.
 */
@Route(value = WelcomeView.ROUTE, layout = PublicLayout.class)
@AnonymousAllowed
public class WelcomeView extends Div implements BeforeEnterObserver, HasDynamicTitle {

    static final String ROUTE = "welcome";

    private final AuthenticationContext authentication;
    private final Paragraph denied = new Paragraph();

    public WelcomeView(AuthenticationContext authentication,
                       @Value("${spring.security.oauth2.client.registration.google.client-id:not-set}") String googleClientId) {
        this.authentication = authentication;
        addClassName("rootology-prose");
        addClassName("rootology-welcome");

        Wordmark brand = new Wordmark();
        brand.addClassName("rootology-welcome-brand");
        add(brand);

        Image mascot = new Image("images/mascot-radish.svg", getTranslation("welcome.mascot"));
        mascot.addClassName("rootology-welcome-mascot");
        add(new H2(getTranslation("welcome.title")), mascot, new Paragraph(getTranslation("welcome.lead")));

        denied.addClassName("rootology-welcome-denied");
        denied.setVisible(false);
        add(denied);

        add(new H3(getTranslation("welcome.sources.title")), new Paragraph(getTranslation("welcome.sources.text")));
        add(new H3(getTranslation("welcome.unsure.title")), new Paragraph(getTranslation("welcome.unsure.text")));
        add(new H3(getTranslation("welcome.weather.title")), new Paragraph(getTranslation("welcome.weather.text")));
        add(new H3(getTranslation("welcome.spaces.title")), new Paragraph(getTranslation("welcome.spaces.text")));

        add(new SignInButtons(getTranslation("loggedout.signin"), getTranslation("signin.google"),
                SecurityConfiguration.googleConfigured(googleClientId)));
        add(new Paragraph(getTranslation("welcome.access")));

        // Sources and Privacy are in the line at the bottom of PublicLayout.
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (authentication.isAuthenticated()) {
            event.rerouteTo(StartView.class);
            return;
        }
        boolean refused = event.getLocation().getQueryParameters().getSingleParameter("error").isPresent();
        denied.setText(refused ? getTranslation("welcome.denied") : "");
        denied.setVisible(refused);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("welcome.page") + " · Rootology";
    }
}
