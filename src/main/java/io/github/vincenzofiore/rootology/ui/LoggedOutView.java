package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Where logout lands. It must be public: sending the browser back to a protected page would
 * start the GitHub flow again, and GitHub, still signed in, would let the user straight back in.
 */
@Route(SecurityConfiguration.LOGGED_OUT_PAGE)
@AnonymousAllowed
public class LoggedOutView extends VerticalLayout implements HasDynamicTitle {

    public LoggedOutView() {
        Anchor signIn = new Anchor(SecurityConfiguration.GITHUB_LOGIN_PAGE, getTranslation("loggedout.signin"));
        signIn.setRouterIgnore(true);
        add(new H2(getTranslation("loggedout.title")), new Paragraph(getTranslation("loggedout.github")), signIn,
                new RouterLink(getTranslation("nav.privacy"), PrivacyView.class));
    }

    @Override
    public String getPageTitle() {
        return getTranslation("loggedout.page") + " · Rootology";
    }
}
