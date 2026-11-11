package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.Span;

/**
 * The service pages, Sources and Privacy, as a small line at the bottom of the side menu and of the public
 * pages, where people look for them, rather than as menu items next to the app's own pages.
 */
class ServiceLinks extends Footer {

    ServiceLinks() {
        addClassName("rootology-service-links");
        add(link(SourcesView.ROUTE, "nav.sources"), new Span("·"), link(PrivacyView.ROUTE, "nav.privacy"));
    }

    private Anchor link(String route, String key) {
        // A plain link: these pages use PublicLayout and are open to people who are not signed in.
        Anchor anchor = new Anchor("/" + route, getTranslation(key));
        anchor.setRouterIgnore(true);
        return anchor;
    }
}
