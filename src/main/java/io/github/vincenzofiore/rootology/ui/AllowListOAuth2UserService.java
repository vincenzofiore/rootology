package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import io.github.vincenzofiore.rootology.spaces.IdentityService;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the GitHub user, finds the person they are, and lets in only: the bootstrap allow-list (environment
 * variable), anyone who already belongs to a space, or someone holding a valid invitation in their session.
 * Without it, any GitHub account could sign in.
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
        return signIn(user, verified, PendingInvitation.current());
    }

    /**
     * Finds the person, admits them, and then does what every sign-in does (see {@link SignInSteps#complete}).
     */
    OAuth2User signIn(OAuth2User user, GitHubEmails.Verified verified, String token) {
        String subject = subject(user);
        String login = resolveLogin(subject, githubLogin(user));
        Set<String> emails = verified.all();
        requireAllowed(login, token, emails);
        steps.complete(login, token, emails, IdentityProvider.GITHUB, subject, verified.primary());
        return withRoles(user, login, emails);
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

    /** Refuses the person unless they are on the bootstrap allow-list, belong to a space, or hold an invitation. */
    void requireAllowed(String login, String token, Set<String> verifiedEmails) {
        boolean allowed = allowedLogins.contains(login)
                || spaces.belongsToAnySpace(login)
                || steps.invitationAdmits(token, login, verifiedEmails);
        if (!allowed) {
            throw denied("GitHub account " + login + " is not allowed");
        }
    }

    private static OAuth2AuthenticationException denied(String reason) {
        return SignInSteps.denied(reason);
    }
}
