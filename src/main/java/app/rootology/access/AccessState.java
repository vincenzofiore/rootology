package app.rootology.access;

import java.time.Instant;

/**
 * What the request page of one identity shows (#65): their latest request, if any
 * ({@code status} null when they never asked), when it was decided and when they may ask again,
 * and whether today's global cap is already reached.
 */
public record AccessState(AccessRequestStatus status, Instant createdAt, Instant decidedAt, Instant retryAfter,
                          String motivation, boolean capReached) {

    public boolean none() {
        return status == null;
    }

    public boolean pending() {
        return status == AccessRequestStatus.PENDING;
    }

    public boolean approved() {
        return status == AccessRequestStatus.APPROVED;
    }

    public boolean rejected() {
        return status == AccessRequestStatus.REJECTED;
    }
}
