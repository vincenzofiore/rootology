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
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the GitHub user, finds the person they are, and lets in only: the bootstrap allow-list (environment
 * variable), anyone who already belongs to a space, or someone holding a valid invitation in their session.
 * Without it, any GitHub account could sign in.
 *
 * <p>The person is found by GitHub's numeric id, which never changes (#28), and by the GitHub login only as a
 * fallback. A login that is not tied to that id yet but whose person already has another GitHub id is refused:
 * it is someone else's old name, freed by a rename. Nothing is written before the person is admitted.
 */
class AllowListOAuth2UserService extends DefaultOAuth2UserService {

    private final Set<String> allowedLogins;
    private final SpaceService spaces;
    private final IdentityService identities;
    private final InvitationService invitations;
    private final RoleService roles;
    private final GitHubEmails gitHubEmails;
    private final NotificationService notifications;

    AllowListOAuth2UserService(Set<String> allowedLogins, SpaceService spaces, IdentityService identities,
                               InvitationService invitations, RoleService roles, GitHubEmails gitHubEmails,
                               NotificationService notifications) {
        this.gitHubEmails = gitHubEmails;
        this.notifications = notifications;
        this.allowedLogins = allowedLogins.stream().map(SpaceService::normalizeLogin).collect(Collectors.toSet());
        this.spaces = spaces;
        this.identities = identities;
        this.invitations = invitations;
        this.roles = roles;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(request);
        GitHubEmails.Verified verified = gitHubEmails.verified(request.getAccessToken().getTokenValue());
        return signIn(user, verified, PendingInvitation.current());
    }

    /**
     * Finds the person, admits them, then accepts a pending invitation, registers them (personal space on first
     * login), records the GitHub identity and remembers their address. Everything here after
     * {@link #requireAllowed} writes only for someone who was admitted.
     */
    OAuth2User signIn(OAuth2User user, GitHubEmails.Verified verified, String token) {
        String subject = subject(user);
        String login = resolveLogin(subject, githubLogin(user));
        Set<String> emails = verified.all();
        requireAllowed(login, token, emails);
        if (token != null && invitations.admits(token, login, emails)) {
            try {
                invitations.accept(token, login, emails);
            } catch (SpaceAccessException e) {
                throw denied(e.getMessage());
            }
            PendingInvitation.clear();
        }
        spaces.signedIn(login);
        try {
            identities.link(login, IdentityProvider.GITHUB, subject, verified.primary());
        } catch (SpaceAccessException e) {
            // Only a concurrent sign-in of the same person can get here; the identity is recorded either way.
            throw denied(e.getMessage());
        }
        notifications.rememberEmail(login, verified.primary());
        return withRoles(user, login, emails);
    }

    /**
     * The person's own login: the one already linked to this GitHub id, otherwise the GitHub login. Refuses a
     * GitHub login whose person is linked to a different GitHub id: someone else's freed name.
     */
    String resolveLogin(String subject, String githubLogin) throws OAuth2AuthenticationException {
        var known = identities.loginOf(IdentityProvider.GITHUB, subject);
        if (known.isPresent()) {
            return known.get();
        }
        String login = SpaceService.normalizeLogin(githubLogin);
        if (identities.hasIdentity(login, IdentityProvider.GITHUB)) {
            throw denied("GitHub login " + login + " belongs to another person");
        }
        return login;
    }

    /** GitHub's numeric id: the account's stable identity, even after a rename. */
    private static String subject(OAuth2User user) throws OAuth2AuthenticationException {
        Object attribute = user.getAttribute("id");
        if (attribute == null) {
            throw denied("GitHub returned no id");
        }
        return String.valueOf(attribute);
    }

    private static String githubLogin(OAuth2User user) throws OAuth2AuthenticationException {
        Object attribute = user.getAttribute("login");
        if (attribute == null) {
            throw denied("GitHub returned no login");
        }
        String login = attribute.toString();
        if (login.isBlank()) {
            throw denied("GitHub returned no login");
        }
        return SpaceService.normalizeLogin(login);
    }

    /**
     * The user with their app roles as authorities (ROLE_ADMIN, ...), read once at sign-in as Spring intends,
     * their verified email addresses as an attribute, for invitations bound to an address (#25), and their
     * internal login, which is also the principal's name.
     */
    OAuth2User withRoles(OAuth2User user, String login, Set<String> verifiedEmails) {
        List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
        roles.rolesOf(login).forEach(role -> authorities.add(new SimpleGrantedAuthority(role.authority())));
        java.util.Map<String, Object> attributes = new java.util.HashMap<>(user.getAttributes());
        attributes.put(GitHubEmails.ATTRIBUTE, List.copyOf(verifiedEmails));
        attributes.put(SignedIn.LOGIN_ATTRIBUTE, login);
        return new DefaultOAuth2User(authorities, attributes, SignedIn.LOGIN_ATTRIBUTE);
    }

    /** Refuses the person unless they are on the bootstrap allow-list, belong to a space, or hold an invitation. */
    void requireAllowed(String login, String token, Set<String> verifiedEmails) {
        boolean allowed = allowedLogins.contains(login)
                || spaces.belongsToAnySpace(login)
                || (token != null && invitations.admits(token, login, verifiedEmails));
        if (!allowed) {
            throw denied("GitHub account " + login + " is not allowed");
        }
    }

    private static OAuth2AuthenticationException denied(String reason) {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied"), reason);
    }
}
