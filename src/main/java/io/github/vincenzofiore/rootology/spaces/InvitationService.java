package io.github.vincenzofiore.rootology.spaces;

import io.github.vincenzofiore.rootology.mail.MailService;
import io.github.vincenzofiore.rootology.mail.OutgoingMail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Invitations to a space: an OWNER creates a single-use, expiring link; the invited person opens
 * it, signs in, and joins. The token only ever exists in the link; the database keeps its SHA-256.
 * An invitation may be bound to a GitHub login or to an email address (#25): then the link is also
 * emailed, and only someone with that address verified on GitHub may accept it.
 */
@Service
public class InvitationService {

    static final Duration VALIDITY = Duration.ofDays(7);
    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final InvitationRepository invitations;
    private final SpaceService spaces;
    private final MailService mail;
    private final String baseUrl;
    private final int dailyLimit;

    InvitationService(InvitationRepository invitations, SpaceService spaces, MailService mail,
                      @Value("${rootology.base-url}") String baseUrl,
                      @Value("${rootology.invitations.daily-limit}") int dailyLimit) {
        this.invitations = invitations;
        this.spaces = spaces;
        this.mail = mail;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.dailyLimit = dailyLimit;
    }

    @Transactional
    public String create(long spaceId, String owner, Role role, String invitedLogin) {
        return create(spaceId, owner, role, invitedLogin, null);
    }

    /**
     * Creates an invitation and returns its token, to be put in a link. It is shown once and cannot be
     * recovered. invitedLogin and invitedEmail may be null; with neither, whoever holds the link may accept
     * it. With an email, the link is also sent there once the invitation is saved; a failed send leaves the
     * invitation valid, and the owner still has the link.
     */
    @Transactional
    public String create(long spaceId, String owner, Role role, String invitedLogin, String invitedEmail) {
        SpaceMember creator = spaces.requireOwner(spaceId, owner);
        String by = SpaceService.normalizeLogin(owner);
        Instant now = Instant.now();
        if (invitations.countByCreatedByAndCreatedAtAfter(by, now.minus(Duration.ofDays(1))) >= dailyLimit) {
            throw new SpaceAccessException("You have created " + dailyLimit + " invitations in the last 24 hours; "
                    + "try again later");
        }
        String email = invitedEmail == null || invitedEmail.isBlank() ? null
                : invitedEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if (email != null && (email.length() > 254 || !EMAIL.matcher(email).matches())) {
            throw new IllegalArgumentException(invitedEmail + " is not an email address");
        }
        invitations.forgetEmailsOfExpired(now);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String restrictedTo = invitedLogin == null || invitedLogin.isBlank()
                ? null : SpaceService.normalizeLogin(invitedLogin);
        Instant expires = now.plus(VALIDITY);
        invitations.save(new Invitation(creator.getSpace(), hash(token), role, restrictedTo, email, by, now, expires));
        log.info("{} invited {} to space {} as {}", owner, restrictedTo != null ? restrictedTo
                : email != null ? MailService.mask(email) : "anyone with the link", spaceId, role);
        if (email != null) {
            OutgoingMail message = InvitationEmail.of(email, creator.getSpace().getName(), role, by,
                    baseUrl + "/invite/" + token, expires);
            // Only once the invitation exists: never email a link that a rollback would make invalid.
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            mail.sendLater(message);
                        }
                    });
        }
        return token;
    }

    @Transactional(readOnly = true)
    public InvitationPreview preview(String token, String login) {
        return preview(token, login, Set.of());
    }

    /**
     * What the invitation page shows; login is null before the person signs in, verifiedEmails are their
     * verified addresses on GitHub (lower case).
     */
    @Transactional(readOnly = true)
    public InvitationPreview preview(String token, String login, Set<String> verifiedEmails) {
        return find(token)
                .map(i -> new InvitationPreview(i.getSpace().getName(), i.getRole(),
                        i.refusal(login == null ? null : SpaceService.normalizeLogin(login), verifiedEmails, Instant.now()),
                        i.getInvitedEmail() == null ? null : MailService.mask(i.getInvitedEmail())))
                .orElseGet(() -> new InvitationPreview(null, null, "This invitation does not exist", null));
    }

    /** True if this person may sign in thanks to this invitation (checked before accepting it). */
    @Transactional(readOnly = true)
    public boolean admits(String token, String login, Set<String> verifiedEmails) {
        return preview(token, login, verifiedEmails).acceptable();
    }

    @Transactional
    public long accept(String token, String login) {
        return accept(token, login, Set.of());
    }

    /** The person joins the space; the invitation is used up. Returns the space id. */
    @Transactional
    public long accept(String token, String login, Set<String> verifiedEmails) {
        String who = SpaceService.normalizeLogin(login);
        Invitation invitation = find(token).orElseThrow(() -> new SpaceAccessException("This invitation does not exist"));
        String refusal = invitation.refusal(who, verifiedEmails, Instant.now());
        if (refusal != null) {
            throw new SpaceAccessException(refusal);
        }
        long spaceId = invitation.getSpace().getId();
        spaces.join(spaceId, who, invitation.getRole());
        invitation.accept(who, Instant.now());
        log.info("{} joined space {} through an invitation by {}", who, spaceId, invitation.getCreatedBy());
        return spaceId;
    }

    /** Pending invitations of a space, for its owners. */
    @Transactional(readOnly = true)
    public List<InvitationSummary> pending(long spaceId, String owner) {
        spaces.requireOwner(spaceId, owner);
        Instant now = Instant.now();
        return invitations.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream()
                .filter(i -> i.isPending(now))
                .map(i -> new InvitationSummary(i.getId(), i.getRole(), i.getInvitedLogin(), i.getInvitedEmail(),
                        i.getCreatedBy(), i.getExpiresAt()))
                .toList();
    }

    @Transactional
    public void revoke(long spaceId, String owner, long invitationId) {
        spaces.requireOwner(spaceId, owner);
        Invitation invitation = invitations.findById(invitationId)
                .filter(i -> i.getSpace().getId() == spaceId)
                .orElseThrow(() -> new SpaceAccessException("No such invitation in space " + spaceId));
        invitation.revoke(Instant.now());
    }

    private Optional<Invitation> find(String token) {
        return token == null || token.isBlank() ? Optional.empty() : invitations.findByTokenHash(hash(token));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
