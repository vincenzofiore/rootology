package io.github.vincenzofiore.rootology.roles;

import java.time.Instant;

/** A grant as the Admin view shows it; revokedAt is null while the role is active. */
public record RoleGrantView(String githubLogin, AppRole role, String grantedBy, Instant grantedAt, String revokedBy,
                            Instant revokedAt) {

    public boolean active() {
        return revokedAt == null;
    }
}
