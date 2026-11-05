package io.github.vincenzofiore.rootology.pipeline;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Stored escalations (#36): export, account deletion and retention (#40, docs/privacy.md). */
@Component
public class EscalationPersonalData {

    private final JdbcClient jdbc;

    EscalationPersonalData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> export(String login) {
        return jdbc.sql("""
                        select space_id, question_text, created_at, started_by, resolved_at, resolved_by
                        from pending_escalation where started_by = :login or resolved_by = :login order by created_at""")
                .param("login", login).query().listOfRows();
    }

    public void forget(String login, Collection<Long> spaces, String replacement) {
        if (!spaces.isEmpty()) {
            jdbc.sql("delete from pending_escalation where space_id in (:spaces)").param("spaces", spaces).update();
        }
        jdbc.sql("update pending_escalation set started_by = :r where started_by = :login").param("r", replacement).param("login", login).update();
        jdbc.sql("update pending_escalation set resolved_by = :r where resolved_by = :login").param("r", replacement).param("login", login).update();
    }

    /** Answered escalations only: a waiting one is still someone's open question. */
    public int purge(Instant cutoff) {
        return jdbc.sql("delete from pending_escalation where resolved_at < :cutoff").param("cutoff", Timestamp.from(cutoff)).update();
    }
}
