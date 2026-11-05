package io.github.vincenzofiore.rootology.notifications;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Notifications and preferences: export, account deletion and retention (#40, docs/privacy.md). */
@Component
public class NotificationPersonalData {

    private final JdbcClient jdbc;

    NotificationPersonalData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> export(String login) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("preferences", jdbc.sql("""
                        select email, email_escalations, email_weather, quiet_from, quiet_to
                        from notification_preference where github_login = :login""")
                .param("login", login).query().listOfRows());
        data.put("notifications", jdbc.sql("""
                        select space_id, kind, title, body, created_at, read_at, emailed_at, resolved_at
                        from notification where recipient = :login order by id""")
                .param("login", login).query().listOfRows());
        return data;
    }

    public void forget(String login, Collection<Long> spaces, String replacement) {
        if (!spaces.isEmpty()) {
            jdbc.sql("delete from notification where space_id in (:spaces)").param("spaces", spaces).update();
        }
        jdbc.sql("delete from notification where recipient = :login").param("login", login).update();
        jdbc.sql("update notification set resolved_by = :r where resolved_by = :login").param("r", replacement).param("login", login).update();
        jdbc.sql("delete from notification_preference where github_login = :login").param("login", login).update();
    }

    /**
     * Notifications read or resolved before the cutoff, and weather alerts older than it, read or not: an
     * alert is about days long past. An unread waiting question stays while it waits.
     */
    public int purge(Instant cutoff) {
        return jdbc.sql("""
                        delete from notification where coalesce(resolved_at, read_at) < :cutoff
                            or (kind = 'WEATHER_ALERT' and created_at < :cutoff)""")
                .param("cutoff", Timestamp.from(cutoff)).update();
    }
}
