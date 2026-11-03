package io.github.vincenzofiore.rootology.ui;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The invitation token a not-yet-signed-in visitor opened, kept in their HTTP session until the
 * GitHub login comes back. Spring Security keeps session attributes across the login.
 */
final class PendingInvitation {

    static final String SESSION_KEY = PendingInvitation.class.getName();

    /** The token waiting in the current request's session, or null. */
    static String current() {
        HttpSession session = session();
        return session == null ? null : (String) session.getAttribute(SESSION_KEY);
    }

    static void clear() {
        HttpSession session = session();
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
    }

    private static HttpSession session() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getSession(false);
        }
        return null;
    }

    private PendingInvitation() {
    }
}
