package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;
import io.github.vincenzofiore.rootology.spaces.InvitationPreview;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;

/**
 * Where an invitation link lands. Public, because the invited person usually has no account yet:
 * the token is kept in the session, the GitHub login lets them in thanks to it, and they join.
 * Someone already signed in joins with one click.
 */
@Route(InvitationView.ROUTE + "/:token")
@PageTitle("Invitation · Rootology")
@AnonymousAllowed
public class InvitationView extends VerticalLayout implements BeforeEnterObserver {

    static final String ROUTE = "invite";

    private final InvitationService invitations;
    private final AuthenticationContext authentication;

    public InvitationView(InvitationService invitations, AuthenticationContext authentication) {
        this.invitations = invitations;
        this.authentication = authentication;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String token = event.getRouteParameters().get("token").orElse("");
        Optional<String> login = authentication.getAuthenticatedUser(OAuth2User.class)
                .map(user -> String.valueOf(user.<Object>getAttribute("login")));
        InvitationPreview preview = invitations.preview(token, login.orElse(null));
        if (!preview.acceptable()) {
            add(new H2("This invitation cannot be used"), new Paragraph(preview.refusal()));
            return;
        }
        add(new H2("You are invited to " + preview.spaceName()),
                new Paragraph("You would join as " + preview.role() + "."));
        if (login.isPresent()) {
            Button join = new Button("Join " + preview.spaceName(), e -> {
                invitations.accept(token, login.get());
                getUI().ifPresent(ui -> ui.navigate(SpaceView.class));
            });
            join.addThemeName("primary");
            add(join, new Paragraph("Then pick the space in the selector at the top."));
        } else {
            VaadinSession.getCurrent().getSession().setAttribute(PendingInvitation.SESSION_KEY, token);
            Anchor signIn = new Anchor(SecurityConfiguration.GITHUB_LOGIN_PAGE, "Sign in with GitHub to join");
            signIn.setRouterIgnore(true);
            add(signIn);
        }
    }
}
