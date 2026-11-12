package app.rootology.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;
import java.util.Map;

/**
 * The old page logout landed on. Logout now lands on the welcome page, which says the person left (#87); this
 * route only forwards there, for bookmarks. It stays public: a protected page would start the sign-in again.
 */
@Route(SecurityConfiguration.LOGGED_OUT_PAGE)
@AnonymousAllowed
public class LoggedOutView extends Div implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.forwardTo(WelcomeView.ROUTE, new QueryParameters(Map.of(WelcomeView.SIGNED_OUT, List.of(""))));
    }
}
