package app.rootology.ui;

import app.rootology.notifications.NotificationView;

/**
 * The details of a notification (#94) in the page’s language: a waiting question says who asked and the
 * question, an answer says who answered, and a weather alert or an invitation request carries its own text
 * (an alert’s is written in Italian). The service stores the data, never a sentence; the rows stored before
 * #94 hold an English sentence written then and have no asker, so they keep showing it as they always did.
 */
final class NotificationText {

    static String details(NotificationView n, NoteText.Texts in) {
        if (n.answered()) {
            // A row stored before #94 has the sentence written then; a new one has no body at all.
            return n.body() != null ? n.body() : in.get("notifications.details.answered", n.resolvedBy());
        }
        if (n.askedBy() == null) {
            // A weather alert, an invitation request, or a waiting question stored before #94.
            return n.body() == null ? "" : n.body();
        }
        return in.get("notifications.details.asked", n.askedBy(), n.body());
    }

    private NotificationText() {
    }
}
