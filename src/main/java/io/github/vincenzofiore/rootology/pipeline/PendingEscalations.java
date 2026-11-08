package io.github.vincenzofiore.rootology.pipeline;

import io.github.vincenzofiore.rootology.decision.Decision;
import io.github.vincenzofiore.rootology.decision.GraphFacts;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Questions waiting for a person, in Postgres (#36): the source of truth, so they survive a restart even
 * though the paused Embabel agents live in memory. Keeps what is needed to finish without the agent.
 */
@Component
class PendingEscalations {

    /** A stored waiting question with what the paused agent knew. */
    record Stored(GardenPipeline.PendingReview review, GraphFacts facts, NoiseProfile noise) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    PendingEscalations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void save(String processId, RunContext context, Question question, Decision decision, GraphFacts facts,
              NoiseProfile noise, Instant at) {
        jdbc.sql("INSERT INTO pending_escalation (process_id, space_id, garden_id, started_by, question_id, "
                        + "question_text, decision_json, facts_json, noise_json, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(processId, context.spaceId(), garden(context.gardenId()), context.startedBy(), question.id(),
                        question.text(),
                        JSON.writeValueAsString(decision), JSON.writeValueAsString(facts),
                        JSON.writeValueAsString(noise == null ? NoiseProfile.NONE : noise), Timestamp.from(at))
                .update();
    }

    /** The waiting questions of one garden, or of the space level (gardenId 0) when it has no garden (#56). */
    List<GardenPipeline.PendingReview> waitingIn(long spaceId, long gardenId) {
        String where = gardenId > 0 ? "garden_id = ?" : "garden_id IS NULL";
        var query = jdbc.sql(SELECT + "WHERE space_id = ? AND resolved_at IS NULL AND " + where
                + " ORDER BY created_at").param(spaceId);
        if (gardenId > 0) {
            query = query.param(gardenId);
        }
        return query.query((rs, i) -> stored(rs).review()).list();
    }

    Optional<Stored> waiting(String processId) {
        return jdbc.sql(SELECT + "WHERE process_id = ? AND resolved_at IS NULL").param(processId)
                .query((rs, i) -> stored(rs)).optional();
    }

    /** The space level is stored as no garden (null), so the column's foreign key can hold. */
    private static Long garden(long gardenId) {
        return gardenId > 0 ? gardenId : null;
    }

    void resolve(String processId, String by, Instant at) {
        jdbc.sql("UPDATE pending_escalation SET resolved_at = ?, resolved_by = ? WHERE process_id = ?")
                .params(Timestamp.from(at), by, processId).update();
    }

    private static final String SELECT = "SELECT process_id, space_id, garden_id, started_by, question_id, "
            + "question_text, decision_json, facts_json, noise_json FROM pending_escalation ";

    private static Stored stored(java.sql.ResultSet rs) throws java.sql.SQLException {
        long garden = rs.getLong("garden_id");
        RunContext context = new RunContext(rs.getLong("space_id"), rs.wasNull() ? 0 : garden,
                rs.getString("started_by"));
        Question question = new Question(rs.getString("question_id"), rs.getString("question_text"));
        Decision decision = JSON.readValue(rs.getString("decision_json"), Decision.class);
        return new Stored(new GardenPipeline.PendingReview(rs.getString("process_id"), context, question, decision),
                JSON.readValue(rs.getString("facts_json"), GraphFacts.class),
                JSON.readValue(rs.getString("noise_json"), NoiseProfile.class));
    }
}
