package app.rootology.notifications;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** The Telegram link of a person: export, account deletion and retention (#40, docs/privacy.md). */
@Component
public class TelegramPersonalData {

    private final JdbcClient jdbc;

    TelegramPersonalData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> export(String login) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("telegramLinks", jdbc.sql("""
                        select chat_id, linked_at, failed_at from telegram_link
                        where login = :login order by linked_at""")
                .param("login", login).query().listOfRows());
        return data;
    }

    public void forget(String login) {
        jdbc.sql("delete from telegram_link where login = :login").param("login", login).update();
        jdbc.sql("delete from telegram_link_code where login = :login").param("login", login).update();
    }

    /** Used or expired codes, and links Telegram stopped delivering to, before the cutoff. */
    public int purge(Instant cutoff) {
        Timestamp at = Timestamp.from(cutoff);
        return jdbc.sql("delete from telegram_link_code where coalesce(used_at, expires_at) < :cutoff")
                .param("cutoff", at).update()
                + jdbc.sql("delete from telegram_link where failed_at < :cutoff").param("cutoff", at).update();
    }
}
