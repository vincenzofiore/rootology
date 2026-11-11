package app.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * The ways into the app (#47): GitHub always, Google only when it is configured (#28).
 * Shared by the welcome page, the invitation page and the logged-out page, so the three
 * pages offer the same ways in. Labels are passed in so each page can word them for its case
 * (joining an invitation, coming back, or just signing in).
 */
class SignInButtons extends HorizontalLayout {

    SignInButtons(String githubLabel, String googleLabel, boolean googleConfigured) {
        Anchor github = new Anchor(SecurityConfiguration.GITHUB_LOGIN_PAGE, githubLabel);
        github.setRouterIgnore(true);
        add(github);
        addClassName("rootology-signin");
        if (googleConfigured) {
            Anchor google = new Anchor(SecurityConfiguration.GOOGLE_LOGIN_PAGE, googleLabel);
            google.setRouterIgnore(true);
            add(google);
        }
    }
}
