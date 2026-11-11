package app.rootology.roles;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One role given to one person; active until revoked, then kept as history. */
@Entity
@Table(name = "app_role_grant")
class RoleGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String githubLogin;

    @Enumerated(EnumType.STRING)
    private AppRole role;

    private String grantedBy;
    private Instant grantedAt;
    private String revokedBy;
    private Instant revokedAt;

    protected RoleGrant() {
    }

    RoleGrant(String githubLogin, AppRole role, String grantedBy, Instant grantedAt) {
        this.githubLogin = githubLogin;
        this.role = role;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    void revoke(String by, Instant at) {
        this.revokedBy = by;
        this.revokedAt = at;
    }

    RoleGrantView view() {
        return new RoleGrantView(githubLogin, role, grantedBy, grantedAt, revokedBy, revokedAt);
    }

    String getGithubLogin() { return githubLogin; }
    AppRole getRole() { return role; }
}
