package io.github.vincenzofiore.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
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
@AnonymousAllowed
public class InvitationView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

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
        Optional<OAuth2User> user = authentication.getAuthenticatedUser(OAuth2User.class);
        Optional<String> login = user.map(u -> u.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE));
        java.util.Set<String> emails = user.map(u -> u.<java.util.List<String>>getAttribute(GitHubEmails.ATTRIBUTE))
                .map(java.util.Set::copyOf).orElse(java.util.Set.of());
        InvitationPreview preview = invitations.preview(token, login.orElse(null), emails);
        if (!preview.acceptable()) {
            add(new H2(getTranslation("invite.refused")), new Paragraph(refusal(preview)));
            return;
        }
        add(new H2(getTranslation("invite.title", preview.spaceName())),
                new Paragraph(getTranslation("invite.as", getTranslation("role." + preview.role().name()))
                        + (preview.forEmail() == null ? "" : " " + getTranslation("invite.foremail", preview.forEmail()))));
        if (login.isPresent()) {
            Button join = new Button(getTranslation("invite.join", preview.spaceName()), e -> {
                invitations.accept(token, login.get(), emails);
                getUI().ifPresent(ui -> ui.navigate(SpaceView.class));
            });
            join.addThemeName("primary");
            add(join, new Paragraph(getTranslation("invite.then")));
        } else {
            VaadinSession.getCurrent().getSession().setAttribute(PendingInvitation.SESSION_KEY, token);
            Anchor signIn = new Anchor(SecurityConfiguration.GITHUB_LOGIN_PAGE, getTranslation("invite.signin"));
            signIn.setRouterIgnore(true);
            add(signIn);
        }
    }

    /** The refusal in the person's language; the service words it in English for its logs. */
    private String refusal(InvitationPreview preview) {
        String text = preview.refusal();
        String key = text.contains("revoked") ? "invite.refused.revoked"
                : text.contains("already used") ? "invite.refused.used"
                : text.contains("expired") ? "invite.refused.expired"
                : text.contains("another GitHub account") ? "invite.refused.account"
                : text.startsWith("This invitation is for ") ? "invite.refused.email"
                : text.contains("does not exist") ? "invite.refused.notfound" : null;
        return key == null ? text : getTranslation(key, preview.forEmail());
    }

    @Override
    public String getPageTitle() {
        return getTranslation("invite.page") + " · Rootology";
    }
}
