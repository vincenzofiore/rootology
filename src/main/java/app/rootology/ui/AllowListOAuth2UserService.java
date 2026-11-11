package app.rootology.ui;

import app.rootology.spaces.IdentityProvider;
import app.rootology.spaces.IdentityService;
import app.rootology.spaces.SpaceService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the GitHub user, finds the person they are, and lets in only: the bootstrap allow-list (environment
 * variable), anyone who already belongs to a space, someone holding a valid invitation in their session, or
 * an identity an admin approved (#65). Anyone else signs in as pending and may only ask for an invitation.
 *
 * <p>The person is found by GitHub's numeric id, which never changes (#28), and by the GitHub login only as a
 * fallback, for a person with no identity at all. A GitHub login whose person already has an identity of any
 * provider is refused: it may be an account of the app under a name derived elsewhere (#28, Google). Nothing is
 * written before the person is admitted.
 */
class AllowListOAuth2UserService extends DefaultOAuth2UserService {

    private final Set<String> allowedLogins;
    private final SpaceService spaces;
    private final IdentityService identities;
    private final SignInSteps steps;
    private final GitHubEmails gitHubEmails;

    AllowListOAuth2UserService(Set<String> allowedLogins, SpaceService spaces, IdentityService identities,
                               SignInSteps steps, GitHubEmails gitHubEmails) {
        this.gitHubEmails = gitHubEmails;
        this.allowedLogins = allowedLogins.stream().map(SpaceService::normalizeLogin).collect(Collectors.toSet());
        this.spaces = spaces;
        this.identities = identities;
        this.steps = steps;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(request);
        GitHubEmails.Verified verified = gitHubEmails.verified(request.getAccessToken().getTokenValue());
        PendingLink.Link marker = PendingLink.current();
        if (marker != null) {
            return link(user, verified, marker);
        }
        return signIn(user, verified, PendingInvitation.current());
    }

    /**
     * A link started in My account (#28): the person is already in, so no admission, no invitation and no new
     * person — only the account just handed over is linked to the marker's person.
     */
    OAuth2User link(OAuth2User user, GitHubEmails.Verified verified, PendingLink.Link marker) {
        String subject = subject(user);
        Set<String> emails = verified.all();
        PendingLink.Result result = steps.link(marker, IdentityProvider.GITHUB, subject, verified.primary());
        if (result != PendingLink.Result.LINKED) {
            throw SignInSteps.refused(result);
        }
        return withRoles(user, SpaceService.normalizeLogin(marker.login()), emails);
    }

    /**
     * Finds the person, admits them, and then does what every sign-in does (see {@link SignInSteps#complete}).
     */
    OAuth2User signIn(OAuth2User user, GitHubEmails.Verified verified, String token) {
        String subject = subject(user);
        String login = resolveLogin(subject, githubLogin(user));
        Set<String> emails = verified.all();
        if (!requireAllowed(login, token, emails, subject)) {
            // #65: not admitted, but allowed in as a pending person, who can ask for an invitation.
            return pending(subject, login, emails);
        }
        steps.complete(login, token, emails, IdentityProvider.GITHUB, subject, verified.primary());
        return withRoles(user, login, emails);
    }

    /** The principal of someone not admitted (#65): no app role, nothing written. */
    OAuth2User pending(String subject, String login, Set<String> emails) {
        return new DefaultOAuth2User(SignInSteps.pendingAuthorities(),
                steps.pendingAttributes(IdentityProvider.GITHUB, subject, login, emails), SignedIn.LOGIN_ATTRIBUTE);
    }

    /**
     * The person's own login: the one already linked to this GitHub id, otherwise the GitHub login, but only for
     * a person with no identity yet. A GitHub login whose person is linked to any account elsewhere is refused:
     * someone else's freed name, or the name the app derived for a Google person.
     */
    String resolveLogin(String subject, String githubLogin) throws OAuth2AuthenticationException {
        var known = identities.loginOf(IdentityProvider.GITHUB, subject);
        if (known.isPresent()) {
            return known.get();
        }
        String login = SpaceService.normalizeLogin(githubLogin);
        if (identities.hasAnyIdentity(login)) {
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
     * The user with their app roles as authorities, their verified email addresses as an attribute, for
     * invitations bound to an address (#25), and their internal login, which is also the principal's name.
     */
    OAuth2User withRoles(OAuth2User user, String login, Set<String> verifiedEmails) {
        return new DefaultOAuth2User(steps.authorities(user, login), steps.attributes(user, login, verifiedEmails),
                SignedIn.LOGIN_ATTRIBUTE);
    }

    /**
     * True when the person is on the bootstrap allow-list, belongs to a space, holds an invitation, or an
     * admin approved their request (#65). False means they sign in as pending.
     */
    boolean requireAllowed(String login, String token, Set<String> verifiedEmails, String subject) {
        return allowedLogins.contains(login)
                || spaces.belongsToAnySpace(login)
                || steps.invitationAdmits(token, login, verifiedEmails)
                || steps.approved(IdentityProvider.GITHUB, subject);
    }

    private static OAuth2AuthenticationException denied(String reason) {
        return SignInSteps.denied(reason);
    }
}
