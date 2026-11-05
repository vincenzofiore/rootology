package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * The frame of the public pages (Sources, Privacy): MainLayout needs a signed-in person, so these pages
 * get a plain bar that leads back to the app, or to sign-in for someone who is not signed in.
 */
@AnonymousAllowed
public class PublicLayout extends Div implements RouterLayout {

    public PublicLayout() {
        boolean signedIn = VaadinRequest.getCurrent() != null && VaadinRequest.getCurrent().getUserPrincipal() != null;
        // A plain link, not a RouterLink: for someone not signed in "/" starts the GitHub sign-in.
        Anchor back = new Anchor("/", "");
        back.setRouterIgnore(!signedIn);
        back.add(VaadinIcon.ARROW_LEFT.create(), new Span(getTranslation(signedIn ? "public.back" : "public.signin")));
        back.addClassName("rootology-public-back");
        Wordmark title = new Wordmark();
        title.addClassName("rootology-public-title");
        Header bar = new Header(back, title);
        bar.addClassName("rootology-public-bar");
        add(bar);
    }
}
