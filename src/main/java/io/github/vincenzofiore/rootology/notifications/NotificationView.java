package io.github.vincenzofiore.rootology.notifications;

import java.time.Instant;

/** A notification as its recipient sees it; processId leads to the waiting question. */
public record NotificationView(long id, long spaceId, String processId, String title, String body, Instant createdAt,
                               Instant readAt, Instant resolvedAt, String resolvedBy) {

    public boolean unread() {
        return readAt == null;
    }
}
