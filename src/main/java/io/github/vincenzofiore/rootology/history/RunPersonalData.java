package io.github.vincenzofiore.rootology.history;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Finished runs: export, account deletion and retention (#40, docs/privacy.md). */
@Component
public class RunPersonalData {

    private final JdbcClient jdbc;

    RunPersonalData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The questions the person asked, and the ones they answered for others. */
    public List<Map<String, Object>> export(String login) {
        return jdbc.sql("""
                        select space_id, question, answer_text, answer, decision_escalated, answered_by_human,
                               reviewer, finished_at, started_by
                        from pipeline_run where started_by = :login or reviewer = :login order by id""")
                .param("login", login).query().listOfRows();
    }

    public void forget(String login, Collection<Long> spaces, String replacement) {
        if (!spaces.isEmpty()) {
            jdbc.sql("delete from pipeline_run where space_id in (:spaces)").param("spaces", spaces).update();
        }
        jdbc.sql("update pipeline_run set started_by = :r where started_by = :login").param("r", replacement).param("login", login).update();
        jdbc.sql("update pipeline_run set reviewer = :r where reviewer = :login").param("r", replacement).param("login", login).update();
    }

    public int purge(Instant cutoff) {
        return jdbc.sql("delete from pipeline_run where finished_at < :cutoff").param("cutoff", Timestamp.from(cutoff)).update();
    }
}
