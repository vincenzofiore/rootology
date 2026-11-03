package io.github.vincenzofiore.rootology.spaces;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A single-use, expiring invitation to a space. Only a hash of its token is stored. */
@Entity
@Table(name = "invitation")
class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id")
    private Space space;

    private String tokenHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String invitedLogin;
    private String createdBy;
    private Instant createdAt;
    private Instant expiresAt;
    private String acceptedBy;
    private Instant acceptedAt;
    private Instant revokedAt;

    protected Invitation() {
    }

    Invitation(Space space, String tokenHash, Role role, String invitedLogin, String createdBy,
               Instant createdAt, Instant expiresAt) {
        this.space = space;
        this.tokenHash = tokenHash;
        this.role = role;
        this.invitedLogin = invitedLogin;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Why this invitation cannot be accepted by this person now, or null if it can. */
    String refusal(String login, Instant now) {
        if (revokedAt != null) {
            return "This invitation was revoked";
        }
        if (acceptedAt != null) {
            return "This invitation was already used";
        }
        if (!now.isBefore(expiresAt)) {
            return "This invitation has expired";
        }
        if (login != null && invitedLogin != null && !invitedLogin.equals(login)) {
            return "This invitation is for another GitHub account";
        }
        return null;
    }

    void accept(String login, Instant now) {
        this.acceptedBy = login;
        this.acceptedAt = now;
    }

    void revoke(Instant now) {
        this.revokedAt = now;
    }

    boolean isPending(Instant now) {
        return revokedAt == null && acceptedAt == null && now.isBefore(expiresAt);
    }

    Long getId() { return id; }
    Space getSpace() { return space; }
    Role getRole() { return role; }
    String getInvitedLogin() { return invitedLogin; }
    String getCreatedBy() { return createdBy; }
    Instant getExpiresAt() { return expiresAt; }
}
