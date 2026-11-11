package app.rootology.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.security.AuthenticationContext;
import app.rootology.spaces.InvitationPreview;
import app.rootology.spaces.InvitationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Optional;

/**
 * Where an invitation link lands. Public, because the invited person usually has no account yet:
 * the token is kept in the session, the GitHub or Google login lets them in thanks to it, and they join.
 * Someone already signed in joins with one click.
 */
@Route(InvitationView.ROUTE + "/:token")
@AnonymousAllowed
public class InvitationView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    static final String ROUTE = "invite";

    private final InvitationService invitations;
    private final AuthenticationContext authentication;
    private final String googleClientId;

    public InvitationView(InvitationService invitations, AuthenticationContext authentication,
                          @Value("${spring.security.oauth2.client.registration.google.client-id:not-set}")
                          String googleClientId) {
        this.invitations = invitations;
        this.authentication = authentication;
        this.googleClientId = googleClientId;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String token = event.getRouteParameters().get("token").orElse("");
        // A pending person (#65) is not admitted yet: like someone signed out, they keep the token in the
        // session and sign in again, and the invitation admits them. Accepting it now would add to a space a
        // login that has no person and no identity.
        Optional<OAuth2User> user = PendingAccess.isPending() ? Optional.empty()
                : authentication.getAuthenticatedUser(OAuth2User.class);
        Optional<String> login = user.map(u -> u.<String>getAttribute(SignedIn.LOGIN_ATTRIBUTE));
        java.util.Set<String> emails = user.map(u -> u.<java.util.List<String>>getAttribute(SignedIn.VERIFIED_EMAILS_ATTRIBUTE))
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
                long spaceId = invitations.accept(token, login.get(), emails);
                getUI().ifPresent(ui -> ui.navigate(GardensView.class,
                        new com.vaadin.flow.router.RouteParameters(java.util.Map.of(Routed.SPACE,
                                String.valueOf(spaceId)))));
            });
            join.addThemeName("primary");
            add(join, new Paragraph(getTranslation("invite.then")));
        } else {
            VaadinSession.getCurrent().getSession().setAttribute(PendingInvitation.SESSION_KEY, token);
            add(signInButtons());
        }
    }

    /**
     * The ways in. GitHub always; Google only when it is configured (#28), since an unconfigured provider would
     * send the person to an error page. Both keep the invitation in the session, so signing in joins them.
     * The buttons come from the shared component so the welcome, invitation and logged-out pages match (#47).
     */
    HorizontalLayout signInButtons() {
        return new SignInButtons(getTranslation("invite.signin"), getTranslation("invite.signin.google"),
                SecurityConfiguration.googleConfigured(googleClientId));
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
