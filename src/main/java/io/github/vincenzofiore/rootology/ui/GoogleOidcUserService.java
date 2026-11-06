package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import io.github.vincenzofiore.rootology.spaces.IdentityService;
import io.github.vincenzofiore.rootology.spaces.InvitationService;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sign in with Google (OpenID Connect, #28). A person is recognised by Google's {@code sub}, which never
 * changes. Someone who is not known yet is a new person: the app never looks for them by email or by login, so
 * nothing is merged by address. They are admitted by the bootstrap list of emails or by a valid invitation,
 * never by belonging to a space (they belong to none), and their login is derived from the address Google
 * verified. Nothing is written before they are admitted.
 */
class GoogleOidcUserService extends OidcUserService {

    /** What a login derived from an address may keep (rule 4 of the plan). */
    private static final String LOGIN_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789.-_";

    private static final int LOGIN_MAX_LENGTH = 39;

    private final Set<String> allowedEmails;
    private final SignInSteps steps;
    private final SpaceService spaces;
    private final IdentityService identities;
    private final InvitationService invitations;

    GoogleOidcUserService(Set<String> allowedEmails, SignInSteps steps, SpaceService spaces,
                          IdentityService identities, InvitationService invitations) {
        this.allowedEmails = allowedEmails.stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.steps = steps;
        this.spaces = spaces;
        this.identities = identities;
        this.invitations = invitations;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(request);
        PendingLink.Link marker = PendingLink.current();
        if (marker != null) {
            return link(user, marker);
        }
        return signIn(user, PendingInvitation.current());
    }

    /**
     * A link started in My account (#28): the person is already in, so no admission, no invitation, no new
     * person and no login derived from the address — only the account just handed over is linked.
     */
    OidcUser link(OidcUser user, PendingLink.Link marker) {
        String subject = subject(user);
        String email = verifiedEmail(user);
        Set<String> emails = emails(email);
        PendingLink.Result result = steps.link(marker, IdentityProvider.GOOGLE, subject, email);
        if (result != PendingLink.Result.LINKED) {
            throw SignInSteps.refused(result);
        }
        return principal(user, marker.login(), emails);
    }

    /** Finds the person (or makes a new one), admits them, then does what every sign-in does (#28). */
    OidcUser signIn(OidcUser user, String sessionInvitation) {
        String subject = subject(user);
        String email = verifiedEmail(user);
        Set<String> emails = emails(email);
        // An invitation naming a GitHub login is not for a Google person: ignore it, and leave it in the
        // session in case they sign in with GitHub.
        String invitation = sessionInvitation != null && !invitations.boundToLogin(sessionInvitation)
                ? sessionInvitation : null;
        var known = identities.loginOf(IdentityProvider.GOOGLE, subject);
        String login;
        if (known.isPresent()) {
            login = known.get();
            requireKnownAllowed(login, email, invitation, emails);
        } else {
            // A new person: without a verified address no login can be derived and no invitation by email checked.
            if (email == null) {
                throw denied("Google returned no verified email");
            }
            login = identities.freeLogin(deriveLogin(email));
            requireNewAllowed(login, email, invitation, emails);
        }
        steps.complete(login, invitation, emails, IdentityProvider.GOOGLE, subject, email);
        return principal(user, login, emails);
    }

    /** Someone already known is admitted as on GitHub: the bootstrap list, a space they belong to, an invitation. */
    void requireKnownAllowed(String login, String email, String invitation, Set<String> verifiedEmails) {
        boolean listed = email != null && allowedEmails.contains(email);
        if (!(listed || spaces.belongsToAnySpace(login) || steps.invitationAdmits(invitation, login, verifiedEmails))) {
            throw denied("Google account " + login + " is not allowed");
        }
    }

    /** A new person only by the bootstrap list of emails or by an invitation anyone with the link may take. */
    void requireNewAllowed(String login, String email, String invitation, Set<String> verifiedEmails) {
        if (!(allowedEmails.contains(email) || steps.invitationAdmits(invitation, login, verifiedEmails))) {
            throw denied("Google account " + email + " is not allowed");
        }
    }

    /** Google's stable id of the account: the {@code sub} claim, the same after an address change. */
    private static String subject(OidcUser user) throws OAuth2AuthenticationException {
        String subject = user.getClaimAsString("sub");
        if (subject == null || subject.isBlank()) {
            throw denied("Google returned no subject");
        }
        return subject;
    }

    /** The address Google verified, in lower case; null when it did not verify one. */
    private static String verifiedEmail(OidcUser user) {
        if (!Boolean.TRUE.equals(user.getClaimAsBoolean("email_verified"))) {
            return null;
        }
        String email = user.getClaimAsString("email");
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** The local part of the address, keeping only the characters a login may have, at most 39 of them. */
    static String deriveLogin(String email) {
        int at = email.indexOf('@');
        String localPart = at < 0 ? email : email.substring(0, at);
        StringBuilder kept = new StringBuilder();
        for (char c : localPart.toLowerCase(Locale.ROOT).toCharArray()) {
            if (LOGIN_CHARACTERS.indexOf(c) >= 0) {
                kept.append(c);
            }
        }
        String login = kept.substring(0, Math.min(kept.length(), LOGIN_MAX_LENGTH));
        return login.isEmpty() ? "user" : login;
    }

    private static Set<String> emails(String email) {
        return email == null ? Set.of() : Set.of(email);
    }

    /**
     * The OIDC principal: the provider's claims in its {@link OidcUserInfo}, plus the internal login and the
     * verified addresses, so the app reads those and never Google's name.
     */
    private OidcUser principal(OidcUser user, String login, Set<String> verifiedEmails) {
        OidcUserInfo info = new OidcUserInfo(steps.attributes(user, login, verifiedEmails));
        return new DefaultOidcUser(steps.authorities(user, login), user.getIdToken(), info, SignedIn.LOGIN_ATTRIBUTE);
    }

    private static OAuth2AuthenticationException denied(String reason) {
        return SignInSteps.denied(reason);
    }
}
