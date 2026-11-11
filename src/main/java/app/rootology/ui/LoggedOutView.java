package app.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.beans.factory.annotation.Value;

/**
 * Where logout lands. It must be public: sending the browser back to a protected page would
 * start the sign-in flow again, and the provider, still signed in, would let the user straight back in.
 * The ways in are the same as the welcome page (#47).
 */
@Route(SecurityConfiguration.LOGGED_OUT_PAGE)
@AnonymousAllowed
public class LoggedOutView extends VerticalLayout implements HasDynamicTitle {

    public LoggedOutView(@Value("${spring.security.oauth2.client.registration.google.client-id:not-set}")
                         String googleClientId) {
        Anchor privacy = new Anchor("/" + PrivacyView.ROUTE, getTranslation("nav.privacy"));
        privacy.setRouterIgnore(true);
        add(new H2(getTranslation("loggedout.title")), new Paragraph(getTranslation("loggedout.github")),
                new SignInButtons(getTranslation("loggedout.signin"), getTranslation("signin.google"),
                        SecurityConfiguration.googleConfigured(googleClientId)),
                privacy);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("loggedout.page") + " · Rootology";
    }
}
