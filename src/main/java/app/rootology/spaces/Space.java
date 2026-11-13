package app.rootology.spaces;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A shared space: a garden or a farm that one or more people work on. */
@Entity
@Table(name = "space")
class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Instant createdAt;

    /** Set for a person's personal space, which they cannot leave. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_of")
    private AppUser personalOf;

    protected Space() {
    }

    Space(String name, Instant createdAt, AppUser personalOf) {
        this.name = name;
        this.createdAt = createdAt;
        this.personalOf = personalOf;
    }

    /** An OWNER gives the space a new name (#79); the service checks the name first. */
    void rename(String name) {
        this.name = name;
    }

    /** True if this is the given person's personal space. */
    boolean isPersonalSpaceOf(AppUser user) {
        return personalOf != null && personalOf.getId().equals(user.getId());
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }
}
