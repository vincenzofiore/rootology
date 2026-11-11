package io.github.vincenzofiore.rootology.access;

import io.github.vincenzofiore.rootology.spaces.IdentityProvider;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Invitation requests (#65): a person who signed in but is not admitted asks once, an admin approves or
 * rejects it, and an approved identity is let in at its next sign-in. The motivation is only ever read by the
 * admins: it is never sent to Claude or Jev, never logged, and shown as plain text.
 *
 * <p>Limits: one open request per identity, a global daily cap ({@code rootology.access-requests.daily-limit},
 * Italian time), and a waiting time after a rejection ({@code rootology.access-requests.retry-after}).
 */
@Service
public class AccessRequestService {

    /** What one motivation may hold: a short paragraph, not an essay. */
    public static final int MAX_MOTIVATION = 500;

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cc}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final AccessRequestRepository requests;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int dailyLimit;
    private final Duration retryAfter;

    AccessRequestService(AccessRequestRepository requests, ApplicationEventPublisher events, Clock clock,
                         @Value("${rootology.access-requests.daily-limit}") int dailyLimit,
                         @Value("${rootology.access-requests.retry-after}") Duration retryAfter) {
        this.requests = requests;
        this.events = events;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
        this.retryAfter = retryAfter;
    }

    /** What this identity has asked and where they stand, for their request page. */
    @Transactional(readOnly = true)
    public AccessState state(IdentityProvider provider, String subject) {
        Instant now = clock.instant();
        boolean capReached = capReached(now);
        return requests.findFirstByProviderAndSubjectOrderByIdDesc(provider, subject)
                .map(request -> new AccessState(request.getStatus(), request.getCreatedAt(), request.getDecidedAt(),
                        request.getStatus() == AccessRequestStatus.REJECTED
                                ? request.getDecidedAt().plus(retryAfter) : null,
                        request.getMotivation(), capReached))
                .orElseGet(() -> new AccessState(null, null, null, null, null, capReached));
    }

    /**
     * Records a request for this identity and tells the admins. Refused without writing when a request is
     * already open, when the person must wait after a rejection, when they were already approved (they only
     * need to sign in again) or when today's cap is reached: a refused request notifies nobody.
     */
    @Transactional
    public AccessState request(IdentityProvider provider, String subject, String login, String email,
                               String motivation) {
        String text = clean(motivation);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A motivation is required");
        }
        if (text.length() > MAX_MOTIVATION) {
            throw new IllegalArgumentException("The motivation is at most " + MAX_MOTIVATION + " characters");
        }
        Instant now = clock.instant();
        AccessState state = state(provider, subject);
        if (state.pending()) {
            throw new IllegalStateException("A request is already waiting for an admin");
        }
        if (state.approved()) {
            throw new IllegalStateException("Access was already approved: sign out and sign in again");
        }
        if (state.rejected() && state.retryAfter().isAfter(now)) {
            throw new IllegalStateException("You may ask again from " + state.retryAfter());
        }
        if (capReached(now)) {
            throw new IllegalStateException("Too many requests today; try again tomorrow");
        }
        String who = login == null ? "" : SpaceService.normalizeLogin(login);
        String address = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        requests.save(new AccessRequest(provider, subject, who, address, text, now));
        events.publishEvent(new AccessRequested());
        return state(provider, subject);
    }

    /** The person takes their open request back: it is deleted, with nothing kept about it. */
    @Transactional
    public void withdraw(IdentityProvider provider, String subject) {
        requests.deleteByProviderAndSubjectAndStatus(provider, subject, AccessRequestStatus.PENDING);
    }

    /** True when an admin approved this identity: its next sign-in is admitted (#65). */
    @Transactional(readOnly = true)
    public boolean isApproved(IdentityProvider provider, String subject) {
        return requests.existsByProviderAndSubjectAndStatus(provider, subject, AccessRequestStatus.APPROVED);
    }

    /**
     * The writes of an admitted sign-in and the request it uses up, in one transaction: the account exists
     * exactly when the approved request is gone. For a person admitted by the allow list or an invitation
     * there is nothing to delete.
     */
    @Transactional
    public void admitted(IdentityProvider provider, String subject, Runnable signIn) {
        signIn.run();
        requests.deleteByProviderAndSubjectAndStatus(provider, subject, AccessRequestStatus.APPROVED);
    }

    /** The requests waiting for a decision, the oldest first, for the admins. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AccessRequestView> pending(String admin) {
        return requests.findByStatusOrderByCreatedAtAsc(AccessRequestStatus.PENDING).stream()
                .map(AccessRequest::view).toList();
    }

    /** The last 30 decided requests, newest decision first, with who decided. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AccessRequestView> decided(String admin) {
        return requests.findTop30ByStatusNotOrderByDecidedAtDesc(AccessRequestStatus.PENDING).stream()
                .map(AccessRequest::view).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void approve(String admin, long requestId) {
        decide(admin, requestId, AccessRequestStatus.APPROVED);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void reject(String admin, long requestId) {
        decide(admin, requestId, AccessRequestStatus.REJECTED);
    }

    private void decide(String admin, long requestId, AccessRequestStatus decision) {
        AccessRequest request = requests.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("No such request: " + requestId));
        request.decide(decision, SpaceService.normalizeLogin(admin), clock.instant());
    }

    /** The retention (#40): decided ones, requests nobody answered, and approvals never used. */
    @Transactional
    public int purge(Instant rejectedBefore, Instant forgottenBefore, Instant unusedApprovalBefore) {
        return requests.deleteDecidedBefore(AccessRequestStatus.REJECTED, rejectedBefore)
                + requests.deleteNeverDecidedBefore(AccessRequestStatus.PENDING, forgottenBefore)
                + requests.deleteNeverDecidedBefore(AccessRequestStatus.APPROVED, unusedApprovalBefore);
    }

    /** A person's own requests, by the identities they signed in with, for their export (#40). */
    @Transactional(readOnly = true)
    public List<AccessRequestView> of(IdentityProvider provider, String subject) {
        return requests.findByProviderAndSubject(provider, subject).stream().map(AccessRequest::view).toList();
    }

    /** Account deletion (#40): the requests of the given identity, gone. */
    @Transactional
    public void forget(IdentityProvider provider, String subject) {
        requests.findByProviderAndSubject(provider, subject).forEach(requests::delete);
    }

    /** True when no more requests may be created today, in Italian time. */
    private boolean capReached(Instant now) {
        Instant midnight = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        return requests.countByCreatedAtGreaterThanEqual(midnight) >= dailyLimit;
    }

    /** Trimmed, without control characters, and with runs of spaces made one: still the person's own words. */
    static String clean(String motivation) {
        if (motivation == null) {
            return "";
        }
        return WHITESPACE.matcher(CONTROL_CHARACTERS.matcher(motivation).replaceAll(" ")).replaceAll(" ").trim();
    }
}
