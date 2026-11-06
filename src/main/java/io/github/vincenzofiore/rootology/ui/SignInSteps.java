package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.roles.RoleService;
import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import io.github.vincenzofiore.rootology.spaces.IdentityService;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.SpaceAccessException;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the GitHub and Google sign-ins share (#28): what happens once a person has been admitted, and the
 * principal both build. Admission itself differs by provider and stays in its own service.
 */
class SignInSteps {

    private final SpaceService spaces;
    private final IdentityService identities;
    private final InvitationService invitations;
    private final RoleService roles;
    private final NotificationService notifications;

    SignInSteps(SpaceService spaces, IdentityService identities, InvitationService invitations, RoleService roles,
                NotificationService notifications) {
        this.spaces = spaces;
        this.identities = identities;
        this.invitations = invitations;
        this.roles = roles;
        this.notifications = notifications;
    }

    /** True if this session holds a pending invitation that this person may accept. */
    boolean invitationAdmits(String token, String login, Set<String> verifiedEmails) {
        return token != null && invitations.admits(token, login, verifiedEmails);
    }

    /**
     * For a person who has just been admitted, in this order: accepts the invitation they hold, signs them in
     * (which creates their personal space on the first login), records the account they came in with and
     * remembers their address. Every write of a sign-in happens here, and only after admission.
     */
    void complete(String login, String token, Set<String> verifiedEmails, IdentityProvider provider, String subject,
                  String email) {
        if (invitationAdmits(token, login, verifiedEmails)) {
            try {
                invitations.accept(token, login, verifiedEmails);
            } catch (SpaceAccessException e) {
                throw denied(e.getMessage());
            }
            PendingInvitation.clear();
        }
        spaces.signedIn(login);
        try {
            identities.link(login, provider, subject, email);
        } catch (SpaceAccessException e) {
            // Only a concurrent sign-in of the same person can get here; the identity is recorded either way.
            throw denied(e.getMessage());
        }
        notifications.rememberEmail(login, email);
    }

    /**
     * Links the account a provider has just handed us to the person who asked for the link in My account (#28),
     * and nothing else: no admission, no invitation, no sign-in of anyone else, no address remembered. The
     * marker is forgotten whatever happens.
     *
     * <p>The rules, in order: an old marker, a marker of another provider and a marker whose person is gone are
     * all {@code EXPIRED}; an account already linked to this person is a success with nothing written; an
     * account linked to another person is {@code TAKEN} and never moved; another account of the same provider
     * is {@code ALREADY_LINKED}.
     */
    PendingLink.Result link(PendingLink.Link marker, IdentityProvider provider, String subject, String email) {
        try {
            if (Instant.now().isAfter(marker.createdAt().plus(PendingLink.WINDOW))) {
                return PendingLink.Result.EXPIRED;
            }
            if (marker.provider() != provider) {
                return PendingLink.Result.EXPIRED;
            }
            String login = SpaceService.normalizeLogin(marker.login());
            if (!identities.isKnownPerson(login)) {
                return PendingLink.Result.EXPIRED;
            }
            Optional<String> owner = identities.loginOf(provider, subject);
            if (owner.filter(login::equals).isPresent()) {
                PendingLink.markLinked();
                return PendingLink.Result.LINKED;
            }
            if (owner.isPresent()) {
                return PendingLink.Result.TAKEN;
            }
            if (identities.hasIdentity(login, provider)) {
                return PendingLink.Result.ALREADY_LINKED;
            }
            identities.link(login, provider, subject, email);
            PendingLink.markLinked();
            return PendingLink.Result.LINKED;
        } catch (SpaceAccessException e) {
            // Only a link racing this one can get here: the account went to someone else meanwhile.
            return PendingLink.Result.TAKEN;
        } finally {
            PendingLink.clear();
        }
    }

    /** The person's app roles as authorities (ROLE_ADMIN, ...), read once at sign-in as Spring intends. */
    List<GrantedAuthority> authorities(OAuth2User user, String login) {
        List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
        roles.rolesOf(login).forEach(role -> authorities.add(new SimpleGrantedAuthority(role.authority())));
        return authorities;
    }

    /** The provider's attributes, plus the internal login and the verified addresses the app itself reads. */
    Map<String, Object> attributes(OAuth2User user, String login, Set<String> verifiedEmails) {
        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.put(SignedIn.VERIFIED_EMAILS_ATTRIBUTE, List.copyOf(verifiedEmails));
        attributes.put(SignedIn.LOGIN_ATTRIBUTE, login);
        return attributes;
    }

    static OAuth2AuthenticationException denied(String reason) {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied"), reason);
    }

    /**
     * A refused link, carrying its code: the failure handler turns this code, and only this code, into the URL
     * of My account (#28).
     */
    static OAuth2AuthenticationException refused(PendingLink.Result result) {
        return new OAuth2AuthenticationException(new OAuth2Error(result.code()),
                "Linking an account was refused: " + result.code());
    }
}
