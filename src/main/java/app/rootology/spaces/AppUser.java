package app.rootology.spaces;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A person, known by their login (stored in lower case), whatever provider they sign in with. */
@Entity
@Table(name = "app_user")
class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String login;
    private Instant createdAt;
    private Instant firstLoginAt;
    /** Chosen by the person (#106); null until then, and then shown as the login. */
    private String displayName;
    /** When the person saw the first-sign-in prompt for a name, chose or skipped it (#106). */
    private Instant nameAskedAt;
    /** When an admin blocked the whole account (#165), and who; both null while the account is open. */
    private Instant blockedAt;
    private String blockedBy;
    /** Why the admin blocked it, when they wrote a reason (#165): shown to the person, never to anyone else. */
    private String blockedReason;

    protected AppUser() {
    }

    AppUser(String login, Instant createdAt) {
        this.login = login;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    String getLogin() {
        return login;
    }

    /** False for a person added to a space who has not signed in yet. */
    boolean hasSignedIn() {
        return firstLoginAt != null;
    }

    void markFirstLogin(Instant now) {
        this.firstLoginAt = now;
    }

    /** The name other people see (#106): the chosen one, otherwise the login. */
    String shownName() {
        return displayName != null ? displayName : login;
    }

    String getDisplayName() {
        return displayName;
    }

    void chooseName(String name, Instant now) {
        this.displayName = name;
        askedForName(now);
    }

    boolean wasAskedForName() {
        return nameAskedAt != null;
    }

    void askedForName(Instant now) {
        if (nameAskedAt == null) {
            nameAskedAt = now;
        }
    }

    /** True while an admin blocks the whole account (#165): it may sign in, but only to see the block. */
    boolean isBlocked() {
        return blockedAt != null;
    }

    void block(String by, String reason, Instant now) {
        this.blockedAt = now;
        this.blockedBy = by;
        this.blockedReason = reason;
    }

    AccountBlock accountBlock() {
        return new AccountBlock(blockedAt, blockedBy, blockedReason);
    }
}
