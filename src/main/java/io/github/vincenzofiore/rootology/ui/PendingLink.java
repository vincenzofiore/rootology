package io.github.vincenzofiore.rootology.ui;

import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * A link of a second sign-in account, started in My account and waiting for the provider to come back (#28):
 * which person asked (their internal login, never something the request says), which provider, and when.
 * Kept in the HTTP session like the invitation token, and read by the providers' user services before their
 * normal sign-in: with a marker there, the person is already in, and only the link happens.
 */
final class PendingLink {

    static final String SESSION_KEY = PendingLink.class.getName();

    /** Set when a link succeeds, read by the sign-in that follows so it lands on My account with the news. */
    static final String LINKED_KEY = PendingLink.class.getName() + ".linked";

    /** How long a started link may take before it is refused. */
    static final Duration WINDOW = Duration.ofMinutes(10);

    /** What was asked: the marker's person, the provider and the moment the link started. */
    record Link(String login, IdentityProvider provider, Instant createdAt) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * How a link ended. The codes are also the error codes of the sign-in's failure and the only thing
     * the failure handler puts in the URL.
     */
    enum Result {

        LINKED("linked"),
        EXPIRED("expired"),
        TAKEN("taken"),
        ALREADY_LINKED("already-linked");

        private final String code;

        Result(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }

        /** The result a failed sign-in stands for, or null when the failure is not a refused link. */
        static Result of(AuthenticationException failure) {
            if (failure instanceof OAuth2AuthenticationException oauth) {
                return byCode(oauth.getError().getErrorCode());
            }
            return null;
        }

        /** The result with this code, or null for anything else (the URL is typed by people). */
        static Result byCode(String code) {
            for (Result result : values()) {
                if (result.code.equals(code)) {
                    return result;
                }
            }
            return null;
        }
    }

    /** Remembers that this person started linking this provider, before sending the browser there. */
    static void start(String login, IdentityProvider provider) {
        HttpSession session = session(true);
        if (session != null) {
            session.removeAttribute(LINKED_KEY);
            session.setAttribute(SESSION_KEY, new Link(login, provider, Instant.now()));
        }
    }

    /** The link waiting in this request's session, or null. */
    static Link current() {
        HttpSession session = session(false);
        return session == null ? null : (Link) session.getAttribute(SESSION_KEY);
    }

    /** Forgets the marker, whatever the link's outcome. */
    static void clear() {
        HttpSession session = session(false);
        if (session != null) {
            session.removeAttribute(SESSION_KEY);
        }
    }

    /** A link has just succeeded. */
    static void markLinked() {
        HttpSession session = session(false);
        if (session != null) {
            session.setAttribute(LINKED_KEY, Boolean.TRUE);
        }
    }

    /** True once if a link just succeeded, then forgotten: the sign-in after it lands in My account. */
    static boolean takeLinked() {
        HttpSession session = session(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(LINKED_KEY))) {
            return false;
        }
        session.removeAttribute(LINKED_KEY);
        return true;
    }

    private static HttpSession session(boolean create) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getSession(create);
        }
        return null;
    }

    private PendingLink() {
    }
}
