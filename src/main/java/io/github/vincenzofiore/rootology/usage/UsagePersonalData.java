package io.github.vincenzofiore.rootology.usage;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Usage records and personal limits: export, account deletion and retention (#40, docs/privacy.md). */
@Component
public class UsagePersonalData {

    private final JdbcClient jdbc;

    UsagePersonalData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> export(String login) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("usage", jdbc.sql("""
                        select space_id, kind, at, claude_calls, prompt_tokens, completion_tokens, cost_usd, jev_calls
                        from usage_record where github_login = :login order by at""")
                .param("login", login).query().listOfRows());
        data.put("dailyLimit", jdbc.sql("select daily_questions, set_at from usage_limit where github_login = :login")
                .param("login", login).query().listOfRows());
        return data;
    }

    public void forget(String login, String replacement) {
        jdbc.sql("delete from usage_record where github_login = :login").param("login", login).update();
        jdbc.sql("delete from usage_limit where github_login = :login").param("login", login).update();
        jdbc.sql("update usage_limit set set_by = :r where set_by = :login").param("r", replacement).param("login", login).update();
    }

    public int purge(Instant cutoff) {
        return jdbc.sql("delete from usage_record where at < :cutoff").param("cutoff", Timestamp.from(cutoff)).update();
    }
}
