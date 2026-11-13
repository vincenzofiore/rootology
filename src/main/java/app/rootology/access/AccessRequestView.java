package app.rootology.access;

import app.rootology.spaces.IdentityProvider;

import java.time.Instant;

/**
 * A request as the admins see it (#65): who asks (provider, login and the verified address), what they wrote,
 * and how it was decided. Plain strings: the view shows them as text, never as markup.
 */
public record AccessRequestView(long id, IdentityProvider provider, String login, String email, String motivation,
                                AccessRequestStatus status, Instant createdAt, String decidedBy, Instant decidedAt) {
}
