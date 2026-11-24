package app.rootology.ui;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;

/**
 * A person whose whole account an admin blocked (#165). At sign-in the principal carries {@link #AUTHORITY}
 * alone, with the internal login and nothing else: no app role, no space, no paid call. AccessRouting sends
 * them from any route to the blocked page and lets them reach only that page and the public ones, where
 * PublicLayout signs them out.
 */
final class BlockedAccess {

    /** The one authority of a blocked principal: no app role ever reaches it. */
    static final String AUTHORITY = "ROLE_BLOCKED";

    /** The authorities of a blocked principal: the block alone, as a pending person gets ROLE_PENDING. */
    static List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(AUTHORITY));
    }

    /** True when the current request's principal was blocked at its sign-in. */
    static boolean isBlocked() {
        return isBlocked(SecurityContextHolder.getContext().getAuthentication());
    }

    private static boolean isBlocked(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> AUTHORITY.equals(authority.getAuthority()));
    }

    /**
     * The internal login of the signed-in principal, or null when there is none: what routing looks up on
     * every navigation to see whether the account was blocked meanwhile (#165).
     */
    static String login() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof OAuth2User principal
                ? principal.getAttribute(SignedIn.LOGIN_ATTRIBUTE) : null;
    }

    private BlockedAccess() {
    }
}
