package app.rootology.ui;

import app.rootology.notifications.NotificationsChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells the open pages of a person that their notifications changed (#26), after the change is committed.
 * In memory: enough for one instance of the app.
 */
@Component
class NotificationBroadcaster {

    private final Map<String, Set<Runnable>> listeners = new ConcurrentHashMap<>();

    /** Returns what to call to stop listening (when the page goes away). */
    Runnable register(String login, Runnable onChange) {
        listeners.computeIfAbsent(login, k -> ConcurrentHashMap.newKeySet()).add(onChange);
        return () -> listeners.getOrDefault(login, Set.of()).remove(onChange);
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(NotificationsChanged changed) {
        changed.logins().forEach(login -> listeners.getOrDefault(login, Set.of()).forEach(Runnable::run));
    }
}
