package app.rootology.notifications;

import java.time.Instant;

/**
 * A notification as its recipient sees it; processId leads to the waiting question (null for weather alerts).
 * gardenId is 0 for a notification about the space level (a question asked outside a garden, #56). dedupKey
 * names the event of a weather alert ("frost:...", "blight:...", #37), so the home can colour it (#84).
 */
public record NotificationView(long id, long spaceId, long gardenId, String kind, String processId, String title,
                               String body, Instant createdAt, Instant readAt, Instant resolvedAt,
                               String resolvedBy, String dedupKey) {

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
