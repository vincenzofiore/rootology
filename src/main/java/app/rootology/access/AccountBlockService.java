package app.rootology.access;

import app.rootology.mail.MailService;
import app.rootology.mail.OutgoingMail;
import app.rootology.roles.RoleService;
import app.rootology.spaces.AccountBlock;
import app.rootology.spaces.AccountBlocks;
import app.rootology.spaces.IdentityService;
import app.rootology.spaces.LinkedIdentity;
import app.rootology.spaces.SpaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Blocking a whole account (#165): an admin takes away a person's access to the app, with an optional short
 * reason, and an email tells the person how to ask for a review; unblocking gives the access back and says so.
 *
 * <p>The person's data stays exactly as it was: spaces, gardens, questions and history are untouched, and the
 * shared spaces keep working for the other members. Only sign-in and use of the app stop. Nobody blocks
 * themselves and the last admin is never blocked, since the app always keeps one.
 *
 * <p>Unblocking clears the block: who unblocked it and when stay in the log only. Nothing of a blocked person
 * is read by Claude or Jev, because they are a member of nothing until an admin unblocks them.
 */
@Service
public class AccountBlockService {

    /** What a reason may hold: one short line, shown to the person in the email and on their page. */
    public static final int MAX_REASON = 200;

    private static final Logger log = LoggerFactory.getLogger(AccountBlockService.class);

    private final AccountBlocks accounts;
    private final RoleService roles;
    private final IdentityService identities;
    private final MailService mail;
    private final AccountFlagRepository flags;
    private final Clock clock;
    /** The controller's contact named in the email, from {@code ROOTOLOGY_PRIVACY_CONTACT}; blank when none. */
    private final String contact;

    AccountBlockService(AccountBlocks accounts, RoleService roles, IdentityService identities, MailService mail,
                        AccountFlagRepository flags, Clock clock,
                        @Value("${rootology.privacy.contact:}") String contact) {
        this.accounts = accounts;
        this.roles = roles;
        this.identities = identities;
        this.mail = mail;
        this.flags = flags;
        this.clock = clock;
        this.contact = contact == null || contact.isBlank() ? null : contact.trim();
    }

    /**
     * True while the account is blocked: read at sign-in, on every navigation and on the person's own page,
     * so an open session stops too (#165).
     */
    @Transactional(readOnly = true)
    public boolean isBlocked(String login) {
        return login != null && accounts.blocked(login);
    }

    /**
     * The block of a person, for the page they see while blocked: empty when their account is open. Only the
     * blocked person reads their own, so no role is needed.
     */
    @Transactional(readOnly = true)
    public Optional<AccountBlock> blockOf(String login) {
        return login == null ? Optional.empty() : accounts.of(login);
    }

    /** Every blocked account, by login, so the admin page offers unblock where it applies (#165). */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Map<String, AccountBlock> blocked() {
        return accounts.all();
    }

    /**
     * Blocks the account from now on, with the reason when the admin wrote one (plain text, at most
     * {@link #MAX_REASON} characters), and emails the person. True when an email went out: false when the app
     * knows no verified address of theirs, which the admin is told.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public boolean block(String admin, String login, String reason) {
        String who = SpaceService.normalizeLogin(login);
        String by = SpaceService.normalizeLogin(admin);
        if (roles.isLastAdmin(who)) {
            throw new IllegalStateException("The last admin cannot be blocked");
        }
        if (who.equals(by)) {
            throw new IllegalArgumentException("Nobody blocks themselves");
        }
        String text = checkedReason(reason);
        accounts.block(who, by, text, clock.instant());
        // A flag that was waiting is closed by this decision, wherever the admin blocked from (#165): the
        // account is never flagged again, also after an unblock.
        flags.findByLoginAndStatus(who, AccountFlagStatus.OPEN)
                .ifPresent(flag -> flag.decide(AccountFlagStatus.BLOCKED, by, clock.instant()));
        log.info("{} blocked the account of {}", by, who);
        return email(who, address -> AccountBlockEmail.blocked(address, text, contact));
    }

    /** Unblocks the account: the person works exactly as before, and hears about it. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public boolean unblock(String admin, String login) {
        String who = SpaceService.normalizeLogin(login);
        String by = SpaceService.normalizeLogin(admin);
        accounts.unblock(who);
        log.info("{} unblocked the account of {}", by, who);
        return email(who, AccountBlockEmail::unblocked);
    }

    /**
     * The reason as stored: one line of plain text, control characters and repeated spaces collapsed, trimmed,
     * at most {@link #MAX_REASON} characters; null when the admin left it empty.
     */
    static String checkedReason(String reason) {
        String clean = reason == null ? "" : reason.replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ").trim();
        if (clean.length() > MAX_REASON) {
            throw new IllegalArgumentException("A reason is at most " + MAX_REASON + " characters");
        }
        return clean.isEmpty() ? null : clean;
    }

    /**
     * Sends the message to the person's verified address, once the change is committed: an email must never
     * speak of something a rollback undid. False when the app knows no address.
     */
    private boolean email(String login, Function<String, OutgoingMail> message) {
        String address = identities.linkedTo(login).stream().map(LinkedIdentity::email)
                .filter(Objects::nonNull).findFirst().orElse(null);
        if (address == null) {
            return false;
        }
        OutgoingMail outgoing = message.apply(address);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            this.mail.sendLater(outgoing);
            return true;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                AccountBlockService.this.mail.sendLater(outgoing);
            }
        });
        return true;
    }
}
