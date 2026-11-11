package app.rootology.access;

import app.rootology.spaces.IdentityProvider;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One request for an invitation (#65), made by an identity (provider and stable id) that is not admitted yet.
 * The login is the one the app gave them, and never changes here: it is only shown to the admins.
 */
@Entity
@Table(name = "access_request")
class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private IdentityProvider provider;

    private String subject;
    private String login;
    private String email;
    private String motivation;

    @Enumerated(EnumType.STRING)
    private AccessRequestStatus status;

    private Instant createdAt;
    private String decidedBy;
    private Instant decidedAt;

    protected AccessRequest() {
    }

    AccessRequest(IdentityProvider provider, String subject, String login, String email, String motivation,
                  Instant createdAt) {
        this.provider = provider;
        this.subject = subject;
        this.login = login;
        this.email = email;
        this.motivation = motivation;
        this.status = AccessRequestStatus.PENDING;
        this.createdAt = createdAt;
    }

    /** Decided once: a request already decided is never decided again. */
    void decide(AccessRequestStatus decision, String admin, Instant at) {
        if (status != AccessRequestStatus.PENDING || decision == AccessRequestStatus.PENDING) {
            throw new IllegalStateException("This request has already been decided");
        }
        this.status = decision;
        this.decidedBy = admin;
        this.decidedAt = at;
    }

    AccessRequestView view() {
        return new AccessRequestView(id, provider, login, email, motivation, status, createdAt, decidedBy, decidedAt);
    }

    long getId() {
        return id;
    }

    IdentityProvider getProvider() {
        return provider;
    }

    String getSubject() {
        return subject;
    }

    String getLogin() {
        return login;
    }

    String getEmail() {
        return email;
    }

    String getMotivation() {
        return motivation;
    }

    AccessRequestStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }
}
