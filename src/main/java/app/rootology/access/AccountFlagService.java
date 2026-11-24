package app.rootology.access;

import app.rootology.garden.GardenService;
import app.rootology.history.RunHistory;
import app.rootology.roles.AppRole;
import app.rootology.roles.RoleService;
import app.rootology.spaces.AccountBlocks;
import app.rootology.spaces.SpaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * The nightly signal that flags an account which only creates useless traffic (#165). An account is flagged
 * when both hold: it signed in at least {@code rootology.suspicious.days-without-garden} days ago and is a
 * member of no space with a garden (a blocked membership does not count), and enough of its last questions
 * were judged by the domain filter, mostly off-domain ({@link SuspiciousAccounts}).
 *
 * <p>The system never decides anything: the flag only tells the admins, and one of them blocks the account or
 * dismisses the flag. An account is flagged once, whatever the decision; blocked accounts and admins are never
 * flagged, and one that an admin blocked and then unblocked carries the BLOCKED flag, so it is not raised again.
 */
@Service
public class AccountFlagService {

    private static final Logger log = LoggerFactory.getLogger(AccountFlagService.class);

    private final AccountFlagRepository flags;
    private final SpaceService spaces;
    private final GardenService gardens;
    private final RunHistory runs;
    private final RoleService roles;
    private final AccountBlocks accounts;
    private final AccountBlockService blocks;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SuspiciousAccounts settings;
    /** One transaction per account, so a single unreadable account never takes the night down with it. */
    private final TransactionTemplate transactions;

    AccountFlagService(AccountFlagRepository flags, SpaceService spaces, GardenService gardens, RunHistory runs,
                       RoleService roles, AccountBlocks accounts, AccountBlockService blocks,
                       ApplicationEventPublisher events, Clock clock, SuspiciousAccounts settings,
                       PlatformTransactionManager transactionManager) {
        this.flags = flags;
        this.spaces = spaces;
        this.gardens = gardens;
        this.runs = runs;
        this.roles = roles;
        this.accounts = accounts;
        this.blocks = blocks;
        this.events = events;
        this.clock = clock;
        this.settings = settings;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * The nightly signal, every night in Italian time: it flags the accounts that meet both halves of the
     * signal and tells the admins. Returns how many accounts it flagged, so the tests and the log can see it.
     */
    @Scheduled(cron = "${rootology.suspicious.cron}", zone = "Europe/Rome")
    public int evaluate() {
        Instant now = clock.instant();
        int flagged = 0;
        for (String login : spaces.people()) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> flag(login, now)))) {
                    flagged++;
                }
            } catch (RuntimeException e) {
                // One unreadable account must not stop the night for the others.
                log.warn("The nightly signal skipped one account: {}", e.getClass().getSimpleName());
            }
        }
        if (flagged > 0) {
            log.info("The nightly signal flagged {} account(s)", flagged);
        }
        return flagged;
    }

    /** Whether one account meets the signal now; flags it and tells the admins when it does. */
    private boolean flag(String login, Instant now) {
        if (accounts.blocked(login) || roles.rolesOf(login).contains(AppRole.ADMIN) || flags.existsByLogin(login)) {
            return false;
        }
        Optional<Instant> first = spaces.firstLoginAt(login);
        if (first.isEmpty()) {
            return false;
        }
        int days = (int) ChronoUnit.DAYS.between(first.get(), now);
        if (days < settings.daysWithoutGarden()
                || spaces.spacesOf(login).stream().anyMatch(space -> gardens.hasGarden(space.id()))) {
            return false;
        }
        RunHistory.JudgedQuestions judged = runs.judgedQuestions(login, settings.judgedQuestions());
        if (judged.judged() < settings.minJudged()
                || (double) judged.offDomain() / judged.judged() < settings.offDomainShare()) {
            return false;
        }
        flags.save(new AccountFlag(login, judged.judged(), judged.offDomain(), days, now));
        events.publishEvent(new AccountFlagged(login, judged.judged(), judged.offDomain(), days));
        log.info("The nightly signal flagged one account");
        return true;
    }

    /** The flags waiting for an admin, newest first, for the Administration page. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AccountFlagView> open() {
        return flags.findByStatusOrderByFlaggedAtDesc(AccountFlagStatus.OPEN).stream()
                .map(AccountFlag::view).toList();
    }

    /**
     * An admin blocks a flagged account, with the optional reason the person will read: the flag is closed as
     * BLOCKED. True when the app could email the person, as {@link AccountBlockService#block} says.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public boolean block(String admin, long flagId, String reason) {
        AccountFlag flag = openFlag(flagId);
        return blocks.block(admin, flag.getLogin(), reason);
    }

    /** An admin leaves the account alone: the flag is closed as DISMISSED and never raised again. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void dismiss(String admin, long flagId) {
        AccountFlag flag = openFlag(flagId);
        flag.decide(AccountFlagStatus.DISMISSED, SpaceService.normalizeLogin(admin), clock.instant());
        log.info("An admin dismissed a flag");
    }

    private AccountFlag openFlag(long flagId) {
        AccountFlag flag = flags.findById(flagId)
                .orElseThrow(() -> new IllegalArgumentException("No such flag: " + flagId));
        if (flag.getStatus() != AccountFlagStatus.OPEN) {
            throw new IllegalStateException("This flag has already been decided");
        }
        return flag;
    }

    /** Retention (#40): decided flags older than the cutoff go; one still waiting for an admin stays. */
    @Transactional
    public int purge(Instant decidedBefore) {
        return flags.deleteDecidedBefore(AccountFlagStatus.OPEN, decidedBefore);
    }
}
