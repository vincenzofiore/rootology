package io.github.vincenzofiore.rootology.history;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.pipeline.ContextSnapshot;
import io.github.vincenzofiore.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The context stored with a question (#55) is a snapshot of names and values, never the pipeline's internal
 * types: a row written today is still read back after a later refactor. A row stored before has none.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StoredContextTest {

    /** The JSON the pipeline writes today, verbatim. */
    static final String WRITTEN_TODAY = """
            {"spaceName":"Casa","gardenName":"Orto sul tetto","zone":"NORD",
             "zoneFrom":"GARDEN","month":"NOVEMBER","monthFrom":"DEFAULT","plotName":"Aiuola B"}""";

    @Autowired
    JdbcClient jdbc;

    @Autowired
    RunHistory history;

    @Autowired
    SpaceService spaces;

    private long spaceOf(String login) {
        spaces.signedIn(login);
        return spaces.spacesOf(login).getFirst().id();
    }

    private void store(String processId, long space, String login, String contextJson) {
        jdbc.sql("INSERT INTO pipeline_run (process_id, space_id, started_by, question_id, question, "
                        + "noise_dropout, noise_spurious, noise_mislabel, noise_near_subject, noise_seed, "
                        + "decision_answer, decision_confidence, decision_escalated, decision_reason, "
                        + "answer, answered_by_human, answer_text, context_json, finished_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 0, 0, false, 0, '{}', 1, false, 'certain', '{}', false, "
                        + "'an answer', ?, now())")
                .params(processId, space, login, "ask", "Cosa cresce?", contextJson)
                .update();
    }

    @Test
    void aStoredContextIsReadBackAsItWasWritten() {
        long space = spaceOf("stored-reader");
        store("stored-1", space, "stored-reader", WRITTEN_TODAY);

        PipelineRunRecord record = history.recent(space, 0, "stored-reader").getFirst();

        assertThat(history.contextOf(record)).contains(new ContextSnapshot("Casa", "Orto sul tetto", "NORD",
                ContextSnapshot.ZoneFrom.GARDEN, "NOVEMBER", ContextSnapshot.MonthFrom.DEFAULT, "Aiuola B"));
    }

    @Test
    void aRowStoredBeforeHasNoContext() {
        long space = spaceOf("stored-old");
        store("stored-2", space, "stored-old", null);

        PipelineRunRecord record = history.recent(space, 0, "stored-old").getFirst();

        assertThat(history.contextOf(record)).isEmpty();
    }
}
