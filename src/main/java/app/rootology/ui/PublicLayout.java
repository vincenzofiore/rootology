package app.rootology.ui;

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
import app.rootology.access.AccountBlockService;
import org.springframework.beans.factory.annotation.Value;

/**
 * The frame of the public pages (Welcome, Sources, Privacy): MainLayout needs a signed-in person, so these
 * pages get a bar with the name and the slogan, and on the right the way back to the app, or "Sign in" for
 * someone who is not signed in (#87). A person
 * waiting for an invitation (#65) has no app to go back to, and "/" or the welcome page would only lead them
 * to their request again: their link signs them out and lands on the welcome page as a visitor.
 */
@AnonymousAllowed
public class PublicLayout extends Div implements RouterLayout, BeforeEnterObserver {

    private final Anchor back;
    private final SignInMenu signIn;
    private final AccountBlockService blocks;
    private final boolean signedIn;
    private final ServiceLinks footer = new ServiceLinks();

    public PublicLayout(
            @Value("${spring.security.oauth2.client.registration.google.client-id:not-set}") String googleClientId,
            AccountBlockService blocks) {
        this.blocks = blocks;
        boolean pending = PendingAccess.isPending();
        // The authority of a sign-in that was blocked, or the account's state, so that a session blocked
        // meanwhile can sign out from these pages too (#165).
        String login = BlockedAccess.login();
        boolean blocked = BlockedAccess.isBlocked() || login != null && blocks.isBlocked(login);
        boolean leaving = pending || blocked;
        boolean principal = VaadinRequest.getCurrent() != null && VaadinRequest.getCurrent().getUserPrincipal() != null;
        signedIn = principal || leaving;
        // A plain link, not a RouterLink: "/" leads a signed-in person into the app.
        back = new Anchor(leaving ? "/" + WelcomeView.ROUTE : "/", "");
        back.setRouterIgnore(leaving);
        back.add(VaadinIcon.ARROW_LEFT.create(), new Span(getTranslation(leaving ? "public.leave" : "public.back")));
        if (leaving) {
            back.getElement().addEventListener("click", e -> leave()).preventDefault();
        }
        back.addClassName("rootology-public-back");
        // A visitor signs in from the top right of every public page (#87); "/" leads them to the welcome page.
        signIn = new SignInMenu(SecurityConfiguration.googleConfigured(googleClientId));
        signIn.setVisible(!signedIn);
        back.setVisible(signedIn);
        Wordmark name = new Wordmark();
        name.addClassName("rootology-public-title");
        Anchor home = new Anchor("/", Wordmark.withSlogan(name));
        home.setRouterIgnore(true);
        home.addClassName("rootology-public-home");
        Header bar = new Header(home, back, signIn);
        bar.addClassName("rootology-public-bar");
        add(bar, footer);
    }

    /** Signs a pending or blocked person out and reloads the welcome page, where they are a visitor again. */
    private static void leave() {
        com.vaadin.flow.component.UI.getCurrent().getPage().setLocation("/" + WelcomeView.ROUTE);
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(
                com.vaadin.flow.server.VaadinServletRequest.getCurrent().getHttpServletRequest(), null, null);
    }

    @Override
    public void showRouterLayoutContent(HasElement content) {
        // The page goes between the bar and the line of service links.
        getElement().insertChild(1, content.getElement());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // People in the app never see the welcome page (WelcomeView sends them Home); elsewhere they go back.
        back.setVisible(signedIn);
        signIn.setVisible(!signedIn);
    }
}
