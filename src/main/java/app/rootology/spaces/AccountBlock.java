package app.rootology.spaces;

import java.time.Instant;

/**
 * A whole account blocked by an admin (#165): when it was blocked, by which admin's login, and the optional
 * reason they wrote. The person's data stays as it is; only the app stops working for them.
 */
public record AccountBlock(Instant blockedAt, String blockedBy, String reason) {
}
