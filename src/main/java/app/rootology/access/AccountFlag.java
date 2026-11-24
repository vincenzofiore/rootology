package app.rootology.access;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One account the nightly signal flagged (#165), with the counts that made it: how many of its last questions
 * the domain filter judged, how many of those were off-domain, and for how long it has had no garden. An admin
 * decides it once, by blocking the account or dismissing the flag.
 */
@Entity
@Table(name = "account_flag")
class AccountFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String login;
    private Instant flaggedAt;
    private int judgedQuestions;
    private int offDomainQuestions;
    private int daysWithoutGarden;

    @Enumerated(EnumType.STRING)
    private AccountFlagStatus status;

    private String decidedBy;
    private Instant decidedAt;

    protected AccountFlag() {
    }

    AccountFlag(String login, int judgedQuestions, int offDomainQuestions, int daysWithoutGarden, Instant flaggedAt) {
        this.login = login;
        this.judgedQuestions = judgedQuestions;
        this.offDomainQuestions = offDomainQuestions;
        this.daysWithoutGarden = daysWithoutGarden;
        this.flaggedAt = flaggedAt;
        this.status = AccountFlagStatus.OPEN;
    }

    /** Decided once: a flag already decided is never decided again. */
    void decide(AccountFlagStatus decision, String admin, Instant at) {
        if (status != AccountFlagStatus.OPEN) {
            throw new IllegalStateException("This flag has already been decided");
        }
        this.status = decision;
        this.decidedBy = admin;
        this.decidedAt = at;
    }

    AccountFlagView view() {
        return new AccountFlagView(id, login, flaggedAt, judgedQuestions, offDomainQuestions, daysWithoutGarden,
                status, decidedBy, decidedAt);
    }

    long getId() {
        return id;
    }

    String getLogin() {
        return login;
    }

    AccountFlagStatus getStatus() {
        return status;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }
}
