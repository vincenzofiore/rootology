package app.rootology.notifications;

import java.time.Instant;

/**
 * A notification as its recipient sees it; processId leads to the waiting question (null for weather alerts).
 * gardenId is 0 for a notification about the space level (a question asked outside a garden, #56). dedupKey
 * names the event of a weather alert ("frost:...", "blight:...", #37), so the home can colour it (#84).
 * body is the question of a waiting question, the text of a weather alert and nothing for an answer (#94);
 * askedBy is who asked a waiting question, null on the rows stored before #94, which keep the sentence the
 * service wrote for them then. A change of membership (#105) carries its event and the space's name.
 */
public record NotificationView(long id, long spaceId, long gardenId, String kind, String processId, String title,
                               String body, Instant createdAt, Instant readAt, Instant resolvedAt,
                               String resolvedBy, String dedupKey, String askedBy, String event, String spaceName) {

    /** A notification stored before #105, or of any kind but a change of membership. */
    public NotificationView(long id, long spaceId, long gardenId, String kind, String processId, String title,
                            String body, Instant createdAt, Instant readAt, Instant resolvedAt, String resolvedBy,
                            String dedupKey, String askedBy) {
        this(id, spaceId, gardenId, kind, processId, title, body, createdAt, readAt, resolvedAt, resolvedBy, dedupKey,
                askedBy, null, null);
    }

    /**
     * A change of membership (#105): event is "ROLE:&lt;role&gt;", "BLOCKED", "UNBLOCKED" or "LEFT", askedBy who
     * acted, body their optional message and spaceName the space's name when it happened.
     */
    public boolean membership() {
        return "MEMBERSHIP".equals(kind);
    }

    public boolean weather() {
        return "WEATHER_ALERT".equals(kind);
    }

    /** An invitation request (#65): no space, it leads to the Administration page. */
    public boolean accessRequest() {
        return "ACCESS_REQUEST".equals(kind);
    }

    /** The asker's own question was answered (#51): it leads to the answer, not to a waiting question. */
    public boolean answered() {
        return "ESCALATION_ANSWERED".equals(kind);
    }

    public boolean unread() {
        return readAt == null;
    }
}
