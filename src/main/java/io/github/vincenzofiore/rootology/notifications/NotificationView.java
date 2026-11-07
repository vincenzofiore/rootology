package io.github.vincenzofiore.rootology.notifications;

import java.time.Instant;

/** A notification as its recipient sees it; processId leads to the waiting question (null for weather alerts). */
public record NotificationView(long id, long spaceId, String kind, String processId, String title, String body,
                               Instant createdAt, Instant readAt, Instant resolvedAt, String resolvedBy) {

    public boolean weather() {
        return "WEATHER_ALERT".equals(kind);
    }

    /** The asker's own question was answered (#51): it leads to the answer, not to a waiting question. */
    public boolean answered() {
        return "ESCALATION_ANSWERED".equals(kind);
    }

    public boolean unread() {
        return readAt == null;
    }
}
