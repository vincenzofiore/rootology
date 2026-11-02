package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Where logout lands. It must be public: sending the browser back to a protected page would
 * start the GitHub flow again, and GitHub, still signed in, would let the user straight back in.
 */
@Route(SecurityConfiguration.LOGGED_OUT_PAGE)
@PageTitle("Logged out · Rootology")
@AnonymousAllowed
public class LoggedOutView extends VerticalLayout {

    public LoggedOutView() {
        Anchor signIn = new Anchor(SecurityConfiguration.GITHUB_LOGIN_PAGE, "Sign in with GitHub");
        signIn.setRouterIgnore(true);
        add(new H2("You are logged out of Rootology"),
                new Paragraph("You may still be signed in to GitHub itself; log out there to switch account."),
                signIn);
    }
}
