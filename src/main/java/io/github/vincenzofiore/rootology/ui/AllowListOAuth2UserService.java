package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.notifications.NotificationService;
import io.github.vincenzofiore.rootology.roles.RoleService;
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
 * Loads the GitHub user and lets in only: the bootstrap allow-list (environment variable),
 * anyone who already belongs to a space, or someone holding a valid invitation in their session.
 * Without it, any GitHub account could sign in.
 */
class AllowListOAuth2UserService extends DefaultOAuth2UserService {

    private final Set<String> allowedLogins;
    private final SpaceService spaces;
    private final InvitationService invitations;
    private final RoleService roles;
    private final GitHubEmails gitHubEmails;
    private final NotificationService notifications;

    AllowListOAuth2UserService(Set<String> allowedLogins, SpaceService spaces, InvitationService invitations,
                               RoleService roles, GitHubEmails gitHubEmails, NotificationService notifications) {
        this.gitHubEmails = gitHubEmails;
        this.notifications = notifications;
        this.allowedLogins = allowedLogins.stream().map(SpaceService::normalizeLogin).collect(Collectors.toSet());
        this.spaces = spaces;
        this.invitations = invitations;
        this.roles = roles;
    }

    /** Accepts a pending invitation first, then registers the person (personal space on first login). */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(request);
        GitHubEmails.Verified verified = gitHubEmails.verified(request.getAccessToken().getTokenValue());
        Set<String> emails = verified.all();
        String token = PendingInvitation.current();
        String login = requireAllowed(user, token, emails);
        if (token != null && invitations.admits(token, login, emails)) {
            try {
                invitations.accept(token, login, emails);
            } catch (SpaceAccessException e) {
                throw denied(e.getMessage());
            }
            PendingInvitation.clear();
        }
        spaces.signedIn(login);
        notifications.rememberEmail(login, verified.primary());
        return withRoles(user, login, emails,
                request.getClientRegistration().getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName());
    }

    /**
     * The user with their app roles as authorities (ROLE_ADMIN, ...), read once at sign-in as Spring intends,
     * and their verified email addresses as an attribute, for invitations bound to an address (#25).
     */
    OAuth2User withRoles(OAuth2User user, String login, Set<String> verifiedEmails, String nameAttribute) {
        List<GrantedAuthority> authorities = new ArrayList<>(user.getAuthorities());
        roles.rolesOf(login).forEach(role -> authorities.add(new SimpleGrantedAuthority(role.authority())));
        java.util.Map<String, Object> attributes = new java.util.HashMap<>(user.getAttributes());
        attributes.put(GitHubEmails.ATTRIBUTE, List.copyOf(verifiedEmails));
        return new DefaultOAuth2User(authorities, attributes, nameAttribute);
    }

    /** The user's login if they may sign in; token is a pending invitation, or null. */
    String requireAllowed(OAuth2User user, String token, Set<String> verifiedEmails) {
        Object attribute = user.getAttribute("login");
        if (attribute == null) {
            throw denied("GitHub returned no login");
        }
        String login = SpaceService.normalizeLogin(attribute.toString());
        boolean allowed = allowedLogins.contains(login)
                || spaces.belongsToAnySpace(login)
                || (token != null && invitations.admits(token, login, verifiedEmails));
        if (!allowed) {
            throw denied("GitHub account " + login + " is not allowed");
        }
        return login;
    }

    private static OAuth2AuthenticationException denied(String reason) {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied"), reason);
    }
}
