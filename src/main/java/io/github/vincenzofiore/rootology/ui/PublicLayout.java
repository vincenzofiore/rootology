package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * The frame of the public pages (Welcome, Sources, Privacy): MainLayout needs a signed-in person, so these
 * pages get a plain bar that leads back to the app, or to sign-in for someone who is not signed in.
 */
@AnonymousAllowed
public class PublicLayout extends Div implements RouterLayout, BeforeEnterObserver {

    private final Anchor back;
    private final boolean signedIn;
    private final ServiceLinks footer = new ServiceLinks();

    public PublicLayout() {
        signedIn = VaadinRequest.getCurrent() != null && VaadinRequest.getCurrent().getUserPrincipal() != null;
        // A plain link, not a RouterLink: for someone not signed in "/" leads to the welcome page (#47).
        back = new Anchor("/", "");
        back.setRouterIgnore(!signedIn);
        back.add(VaadinIcon.ARROW_LEFT.create(), new Span(getTranslation(signedIn ? "public.back" : "public.signin")));
        back.addClassName("rootology-public-back");
        Wordmark title = new Wordmark();
        title.addClassName("rootology-public-title");
        Header bar = new Header(back, title);
        bar.addClassName("rootology-public-bar");
        add(bar, footer);
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        // The page goes between the bar and the line of service links.
        getElement().insertChild(1, content.getElement());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // On the welcome page a visitor would find a link back to the page itself, since "/" leads there (#50).
        // Signed-in people never see the welcome page: they are sent to Home by WelcomeView.
        boolean onWelcome = WelcomeView.class.equals(event.getNavigationTarget());
        back.setVisible(!onWelcome || signedIn);
    }
}
