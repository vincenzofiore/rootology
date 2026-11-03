package io.github.vincenzofiore.rootology.spaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * Invitations to a space: an OWNER creates a single-use, expiring link; the invited person opens
 * it, signs in, and joins. The token only ever exists in the link; the database keeps its SHA-256.
 */
@Service
public class InvitationService {

    static final Duration VALIDITY = Duration.ofDays(7);
    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvitationRepository invitations;
    private final SpaceService spaces;

    InvitationService(InvitationRepository invitations, SpaceService spaces) {
        this.invitations = invitations;
        this.spaces = spaces;
    }

    /**
     * Creates an invitation and returns its token, to be put in a link. It is shown once and
     * cannot be recovered. invitedLogin may be null, so whoever holds the link may accept it.
     */
    @Transactional
    public String create(long spaceId, String owner, Role role, String invitedLogin) {
        SpaceMember creator = spaces.requireOwner(spaceId, owner);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String restrictedTo = invitedLogin == null || invitedLogin.isBlank()
                ? null : SpaceService.normalizeLogin(invitedLogin);
        Instant now = Instant.now();
        invitations.save(new Invitation(creator.getSpace(), hash(token), role, restrictedTo,
                SpaceService.normalizeLogin(owner), now, now.plus(VALIDITY)));
        log.info("{} invited {} to space {} as {}", owner, restrictedTo == null ? "anyone with the link" : restrictedTo,
                spaceId, role);
        return token;
    }

    /** What the invitation page shows; login is null before the person signs in. */
    @Transactional(readOnly = true)
    public InvitationPreview preview(String token, String login) {
        return find(token)
                .map(i -> new InvitationPreview(i.getSpace().getName(), i.getRole(),
                        i.refusal(login == null ? null : SpaceService.normalizeLogin(login), Instant.now())))
                .orElseGet(() -> new InvitationPreview(null, null, "This invitation does not exist"));
    }

    /** True if this person may sign in thanks to this invitation (checked before accepting it). */
    @Transactional(readOnly = true)
    public boolean admits(String token, String login) {
        return preview(token, login).acceptable();
    }

    /** The person joins the space; the invitation is used up. Returns the space id. */
    @Transactional
    public long accept(String token, String login) {
        String who = SpaceService.normalizeLogin(login);
        Invitation invitation = find(token).orElseThrow(() -> new SpaceAccessException("This invitation does not exist"));
        String refusal = invitation.refusal(who, Instant.now());
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
                .map(i -> new InvitationSummary(i.getId(), i.getRole(), i.getInvitedLogin(), i.getCreatedBy(),
                        i.getExpiresAt()))
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
