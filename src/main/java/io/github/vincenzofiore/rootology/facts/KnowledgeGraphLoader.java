package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.NeedRow;
import org.neo4j.driver.Driver;
import org.neo4j.driver.TransactionContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the curated knowledge base into Neo4j, with the `Kb` label on every node so it stays
 * apart from the quality suite's dataset:
 * (:Crop)-[:BELONGS_TO]->(:Family), (:Crop)-[:COMPANION_OF|ANTAGONIST_OF]->(:Crop),
 * (:Organism)-[:AFFECTS]->(:Crop), (:Crop)-[:WINDOW]->(:Zone), and (:Source) nodes.
 * Every relationship carries the ids of its sources.
 */
final class KnowledgeGraphLoader {

    static final List<String> CONSTRAINTS = List.of(
            "CREATE CONSTRAINT kb_crop_id IF NOT EXISTS FOR (n:Crop) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT kb_family_name IF NOT EXISTS FOR (n:Family) REQUIRE n.name IS UNIQUE",
            "CREATE CONSTRAINT kb_organism_id IF NOT EXISTS FOR (n:Organism) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT kb_zone_id IF NOT EXISTS FOR (n:Zone) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT kb_source_id IF NOT EXISTS FOR (n:Source) REQUIRE n.id IS UNIQUE");

    static void createConstraints(Driver driver) {
        try (var session = driver.session()) {
            CONSTRAINTS.forEach(c -> session.run(c).consume());
        }
    }

    static void load(TransactionContext tx, CuratedKnowledge k) {
        tx.run("UNWIND $rows AS s CREATE (:Kb:Source {id: s.id, title: s.title, url: s.url, license: s.license, "
                + "retrievedOn: s.retrievedOn})", rows(k.sources().stream().map(s -> map(
                "id", s.id(), "title", s.title(), "url", s.url(), "license", s.license(),
                "retrievedOn", s.retrievedOn())).toList())).consume();

        tx.run("UNWIND $rows AS c CREATE (n:Kb:Crop {id: c.id, italianName: c.italianName, englishName: c.englishName, "
                + "scientificName: c.scientificName, wikidataId: c.wikidataId, ecocropId: c.ecocropId}) "
                + "MERGE (f:Kb:Family {name: c.family}) CREATE (n)-[:BELONGS_TO {sources: ['wikidata']}]->(f)",
                rows(k.crops().stream().map(c -> map("id", c.id(), "italianName", c.italianName(),
                        "englishName", c.englishName(), "scientificName", c.scientificName(), "family", c.family(),
                        "wikidataId", c.wikidataId(), "ecocropId", c.ecocropId())).toList())).consume();

        tx.run("UNWIND $rows AS n MATCH (c:Kb:Crop {id: n.cropId}) SET c += n.needs",
                rows(k.needs().stream().map(n -> map("cropId", n.cropId(), "needs", needs(n))).toList())).consume();

        tx.run("UNWIND $rows AS w MATCH (c:Kb:Crop {id: w.cropId}) MERGE (z:Kb:Zone {id: w.zone}) "
                + "CREATE (c)-[:WINDOW {activity: w.activity, fromMonth: w.fromMonth, toMonth: w.toMonth, "
                + "note: w.note, sources: [w.source], verified: w.verified}]->(z)",
                rows(k.windows().stream().map(w -> map("cropId", w.cropId(), "zone", w.zone(),
                        "activity", w.activity(), "fromMonth", w.fromMonth(), "toMonth", w.toMonth(),
                        "note", w.note(), "source", w.source(), "verified", w.verified())).toList())).consume();

        for (String relation : KnowledgeValidator.RELATIONS) {
            // The relationship type comes from the validated set, never from the files.
            tx.run("UNWIND $rows AS p MATCH (a:Kb:Crop {id: p.cropId}), (b:Kb:Crop {id: p.partnerId}) "
                    + "CREATE (a)-[:" + relation + "_OF {effect: p.effect, evidence: p.evidence, sources: p.sources}]->(b)",
                    rows(k.pairs().stream().filter(p -> p.relation().equals(relation)).map(p -> map(
                            "cropId", p.cropId(), "partnerId", p.partnerId(), "effect", p.effect(),
                            "evidence", p.evidence(), "sources", p.sources())).toList())).consume();
        }

        tx.run("UNWIND $rows AS o CREATE (:Kb:Organism {id: o.id, italianName: o.italianName, "
                + "englishName: o.englishName, scientificName: o.scientificName, kind: o.kind, sources: [o.source]})",
                rows(k.organisms().stream().map(o -> map("id", o.id(), "italianName", o.italianName(),
                        "englishName", o.englishName(), "scientificName", o.scientificName(), "kind", o.kind(),
                        "source", o.source())).toList())).consume();

        tx.run("UNWIND $rows AS h MATCH (o:Kb:Organism {id: h.organismId}), (c:Kb:Crop {id: h.cropId}) "
                + "CREATE (o)-[:AFFECTS {fromMonth: h.fromMonth, toMonth: h.toMonth, note: h.note, "
                + "sources: [h.source], verified: h.verified}]->(c)",
                rows(k.hosts().stream().map(h -> map("organismId", h.organismId(), "cropId", h.cropId(),
                        "fromMonth", h.fromMonth(), "toMonth", h.toMonth(), "note", h.note(),
                        "source", h.source(), "verified", h.verified())).toList())).consume();
    }

    /** Only the values ECOCROP gives; absent ones are not stored as properties. */
    private static Map<String, Object> needs(NeedRow n) {
        Map<String, Object> m = new HashMap<>();
        put(m, "tempOptMin", n.tempOptMin());
        put(m, "tempOptMax", n.tempOptMax());
        put(m, "tempAbsMin", n.tempAbsMin());
        put(m, "tempAbsMax", n.tempAbsMax());
        put(m, "rainOptMin", n.rainOptMin());
        put(m, "rainOptMax", n.rainOptMax());
        put(m, "phOptMin", n.phOptMin());
        put(m, "phOptMax", n.phOptMax());
        put(m, "phAbsMin", n.phAbsMin());
        put(m, "phAbsMax", n.phAbsMax());
        put(m, "cycleMinDays", n.cycleMinDays());
        put(m, "cycleMaxDays", n.cycleMaxDays());
        put(m, "soilTexture", n.soilTexture());
        put(m, "soilDrainage", n.soilDrainage());
        put(m, "light", n.light());
        m.put("needsSources", List.of(n.source()));
        return m;
    }

    private static void put(Map<String, Object> m, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isEmpty())) {
            m.put(key, value);
        }
    }

    private static Map<String, Object> rows(List<Map<String, Object>> rows) {
        return Map.of("rows", rows);
    }

    /** A map that accepts null values (Map.of does not). */
    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            m.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return m;
    }

    private KnowledgeGraphLoader() {
    }
}
