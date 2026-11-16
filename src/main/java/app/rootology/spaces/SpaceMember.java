package app.rootology.spaces;

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

/** One person's membership of one space, with their role there, and whether an owner blocked it (#105). */
@Entity
@Table(name = "space_member")
class SpaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id")
    private Space space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Instant joinedAt;

    /** Set while an owner has blocked the member (#105): listed, but no longer a member for anything else. */
    private Instant blockedAt;

    private String blockedBy;

    protected SpaceMember() {
    }

    SpaceMember(Space space, AppUser user, Role role, Instant joinedAt) {
        this.space = space;
        this.user = user;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    Space getSpace() {
        return space;
    }

    AppUser getUser() {
        return user;
    }

    Role getRole() {
        return role;
    }

    void changeRole(Role role) {
        this.role = role;
    }

    boolean isBlocked() {
        return blockedAt != null;
    }

    void block(String by, Instant at) {
        this.blockedAt = at;
        this.blockedBy = by;
    }

    void unblock() {
        this.blockedAt = null;
        this.blockedBy = null;
    }
}
