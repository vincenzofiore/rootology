package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Someone who signed in with GitHub or Google but is not admitted (#65). The principal carries
 * {@link #AUTHORITY} alone, the identity the provider gave them and the login the app would give them, and
 * nothing was written for them: no person, no identity, no space, no remembered address. They may only ask
 * for an invitation (see AccessRouting); everyone else treats them as a stranger.
 */
final class PendingAccess {

    /** The one authority of a pending principal: no app role ever reaches it. */
    static final String AUTHORITY = "ROLE_PENDING";

    /** The provider and its stable id, so the person can make a request for the identity they came with. */
    static final String PROVIDER_ATTRIBUTE = "rootology_pending_provider";
    static final String SUBJECT_ATTRIBUTE = "rootology_pending_subject";

    /** The identity of a pending person, with the login the app would give them. */
    record Identity(IdentityProvider provider, String subject, String login, Set<String> emails) {
    }

    /** True when the current request's principal is a pending person (#65). */
    static boolean isPending() {
        return isPending(SecurityContextHolder.getContext().getAuthentication());
    }

    private static boolean isPending(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> AUTHORITY.equals(authority.getAuthority()));
    }

    /** True when the current request is an authenticated person, pending or admitted. */
    static boolean signedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }

    /** The pending person of the current request, if it is one. */
    static Optional<Identity> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isPending(authentication) || !(authentication.getPrincipal()
                instanceof org.springframework.security.oauth2.core.user.OAuth2User principal)) {
            return Optional.empty();
        }
        String provider = principal.getAttribute(PROVIDER_ATTRIBUTE);
        String subject = principal.getAttribute(SUBJECT_ATTRIBUTE);
        String login = principal.getAttribute(SignedIn.LOGIN_ATTRIBUTE);
        if (provider == null || subject == null || login == null) {
            return Optional.empty();
        }
        List<String> emails = principal.getAttribute(SignedIn.VERIFIED_EMAILS_ATTRIBUTE);
        return Optional.of(new Identity(IdentityProvider.valueOf(provider), subject, login,
                emails == null ? Set.of() : Set.copyOf(emails)));
    }

    private PendingAccess() {
    }
}
