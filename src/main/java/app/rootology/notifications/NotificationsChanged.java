package app.rootology.notifications;

import java.util.Set;

/** The notifications of these people changed: open pages refresh their counter (in-memory, one instance). */
public record NotificationsChanged(Set<String> logins) {
}
