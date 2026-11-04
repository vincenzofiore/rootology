package io.github.vincenzofiore.rootology.spaces;

import java.time.Instant;

/** A pending invitation, as its space's owners see it (never the token). */
public record InvitationSummary(long id, Role role, String invitedLogin, String invitedEmail, String createdBy,
                                Instant expiresAt) {
}
