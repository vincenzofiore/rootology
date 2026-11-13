package app.rootology.spaces;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One external identity of a person (#28): the provider and the stable id it gives them, so the person is
 * found again even when their name there changes. A person may have several, one per provider, linked from
 * My account and never merged automatically.
 */
@Entity
@Table(name = "user_identity")
class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Convert(converter = IdentityProviderConverter.class)
    private IdentityProvider provider;

    private String subject;
    private String email;
    private Instant linkedAt;

    protected UserIdentity() {
    }

    UserIdentity(AppUser user, IdentityProvider provider, String subject, String email, Instant linkedAt) {
        this.user = user;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
        this.linkedAt = linkedAt;
    }

    long getId() {
        return id;
    }

    AppUser getUser() {
        return user;
    }

    IdentityProvider getProvider() {
        return provider;
    }

    String getSubject() {
        return subject;
    }

    /** The address the provider verified, when it gave us one. */
    String getEmail() {
        return email;
    }

    Instant getLinkedAt() {
        return linkedAt;
    }
}
