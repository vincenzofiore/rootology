package io.github.vincenzofiore.rootology.facts;

import org.neo4j.driver.Driver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the curated knowledge base in Neo4j (label `Kb`), never the quality suite's dataset. */
@Component
public class KnowledgeGraph {

    /** A declared pair as seen from one crop. */
    public record CropPair(String cropId, String partnerId, String relation, String effect, String evidence,
                           List<String> sources) {
    }

    /** A pest or disease that affects a crop in a given month. */
    public record Affecting(String organismId, String italianName, String kind, int fromMonth, int toMonth,
                            List<String> sources, boolean verified) {
    }

    private static final Set<String> LABELS = Set.of("Crop", "Family", "Organism", "Zone", "Source");

    private final Driver driver;

    public KnowledgeGraph(Driver driver) {
        this.driver = driver;
    }

    public long count(String label) {
        if (!LABELS.contains(label)) {
            throw new IllegalArgumentException("Unknown knowledge base label " + label);
        }
        return single("MATCH (n:Kb:" + label + ") RETURN count(n) AS n", Map.of()).get("n").asLong();
    }

    /** The pairs declared from this crop (the reverse direction is never inferred). */
    public List<CropPair> pairsOf(String cropId) {
        return list("MATCH (a:Kb:Crop {id: $id})-[r:COMPANION_OF|ANTAGONIST_OF]->(b:Kb:Crop) "
                        + "RETURN a.id AS a, b.id AS b, type(r) AS t, r.effect AS effect, r.evidence AS evidence, "
                        + "r.sources AS sources ORDER BY b.id", Map.of("id", cropId),
                r -> new CropPair(r.get("a").asString(), r.get("b").asString(),
                        r.get("t").asString().replace("_OF", ""), r.get("effect").asString(),
                        r.get("evidence").asString(), r.get("sources").asList(v -> v.asString())));
    }

    /** Pests and diseases active on this crop in this month (1-12); periods may wrap over the new year. */
    public List<Affecting> affecting(String cropId, int month) {
        return list("MATCH (o:Kb:Organism)-[a:AFFECTS]->(:Kb:Crop {id: $id}) "
                        + "WHERE (a.fromMonth <= a.toMonth AND $m >= a.fromMonth AND $m <= a.toMonth) "
                        + "   OR (a.fromMonth > a.toMonth AND ($m >= a.fromMonth OR $m <= a.toMonth)) "
                        + "RETURN o.id AS id, o.italianName AS name, o.kind AS kind, a.fromMonth AS f, a.toMonth AS t, "
                        + "a.sources AS sources, a.verified AS verified ORDER BY o.id",
                Map.of("id", cropId, "m", month),
                r -> new Affecting(r.get("id").asString(), r.get("name").asString(), r.get("kind").asString(),
                        r.get("f").asInt(), r.get("t").asInt(), r.get("sources").asList(v -> v.asString()),
                        r.get("verified").asBoolean()));
    }

    private org.neo4j.driver.Record single(String query, Map<String, Object> params) {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run(query, params).single());
        }
    }

    private <T> List<T> list(String query, Map<String, Object> params,
                             java.util.function.Function<org.neo4j.driver.Record, T> mapper) {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run(query, params).list(mapper::apply));
        }
    }
}
