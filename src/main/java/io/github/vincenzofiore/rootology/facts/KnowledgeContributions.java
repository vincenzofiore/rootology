package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.facts.Contribution.Alias;
import io.github.vincenzofiore.rootology.facts.Contribution.Host;
import io.github.vincenzofiore.rootology.facts.Contribution.Pair;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks and writes contributed facts in Neo4j. A global fact (spaceId null) is visible to everyone,
 * with provenance CURATED once a curator approved it; a space fact is visible only to its space, with
 * provenance PROPOSED. Every contributed node or relationship carries its proposal id and the source
 * id `proposal-<id>`. Checks follow the curated files' rules (known ids, months, no self pairs, no pair
 * both companion and antagonist) and refuse facts that are already known.
 */
@Component
public class KnowledgeContributions {

    static final List<String> LANGUAGES = List.of("it", "en");

    private final Driver driver;

    public KnowledgeContributions(Driver driver) {
        this.driver = driver;
    }

    /** What is wrong with this contribution, as seen by this space (null: globally); empty means valid. */
    public List<String> problems(Contribution c, Long spaceId) {
        List<String> problems = new ArrayList<>();
        switch (c) {
            case Alias a -> {
                if (!exists("MATCH (e:Kb) WHERE (e:Crop OR e:Organism) AND e.id = $id RETURN count(e) > 0 AS ok",
                        Map.of("id", nn(a.targetId())))) {
                    problems.add("unknown crop or organism " + a.targetId());
                }
                if (blank(a.name()) || a.name().length() > 100) {
                    problems.add("a name must have 1 to 100 characters");
                } else {
                    List<String> named = ids("MATCH (e:Kb) WHERE (e:Crop OR e:Organism) AND (toLower(e.id) = $n "
                            + "OR toLower(e.italianName) = $n OR toLower(e.englishName) = $n "
                            + "OR toLower(e.scientificName) = $n OR EXISTS { MATCH (x:Kb:Alias)-[:NAME_OF]->(e) "
                            + "WHERE x.name = $n AND " + visible("x") + " }) RETURN e.id AS id",
                            params(spaceId, "n", normalize(a.name())));
                    if (named.contains(a.targetId())) {
                        problems.add("\"" + a.name() + "\" is already a name of " + a.targetId());
                    } else if (!named.isEmpty()) {
                        problems.add("\"" + a.name() + "\" already names " + named.getFirst());
                    }
                }
                if (!LANGUAGES.contains(a.language())) {
                    problems.add("language must be one of " + LANGUAGES + ", not " + a.language());
                }
            }
            case Pair p -> {
                crop(p.cropId(), problems);
                crop(p.partnerId(), problems);
                if (p.cropId() != null && p.cropId().equals(p.partnerId())) {
                    problems.add("a crop cannot pair with itself");
                }
                if (!KnowledgeValidator.RELATIONS.contains(p.relation())) {
                    problems.add("relation must be one of " + KnowledgeValidator.RELATIONS + ", not " + p.relation());
                } else {
                    // Either direction counts: the same pair cannot be both companion and antagonist.
                    List<String> declared = ids("MATCH (a:Kb:Crop)-[r:COMPANION_OF|ANTAGONIST_OF]-(b:Kb:Crop) "
                            + "WHERE a.id = $a AND b.id = $b AND " + visible("r") + " RETURN type(r) AS id",
                            params(spaceId, "a", nn(p.cropId()), "b", nn(p.partnerId())));
                    if (declared.contains(p.relation() + "_OF")) {
                        problems.add("this pair is already declared");
                    } else if (!declared.isEmpty()) {
                        problems.add("this pair is already declared as " + declared.getFirst().replace("_OF", "")
                                .toLowerCase(Locale.ROOT));
                    }
                }
                if (blank(p.effect()) || p.effect().length() > 300) {
                    problems.add("an effect must have 1 to 300 characters");
                }
            }
            case Host h -> {
                if (!exists("MATCH (o:Kb:Organism {id: $id}) RETURN count(o) > 0 AS ok", Map.of("id", nn(h.organismId())))) {
                    problems.add("unknown organism " + h.organismId());
                }
                crop(h.cropId(), problems);
                month(h.fromMonth(), problems);
                month(h.toMonth(), problems);
                if (exists("MATCH (:Kb:Organism {id: $o})-[r:AFFECTS]->(:Kb:Crop {id: $c}) WHERE " + visible("r")
                        + " RETURN count(r) > 0 AS ok", params(spaceId, "o", nn(h.organismId()), "c", nn(h.cropId())))) {
                    problems.add("this organism is already declared on this crop");
                }
            }
        }
        return problems;
    }

    /** Writes an approved global fact (spaceId null) or a space fact. Callers check {@link #problems} first. */
    public void apply(long proposalId, Long spaceId, Contribution c) {
        Map<String, Object> p = new HashMap<>();
        p.put("proposalId", proposalId);
        p.put("spaceId", spaceId);
        p.put("provenance", spaceId == null ? "CURATED" : "PROPOSED");
        p.put("sources", List.of("proposal-" + proposalId));
        String query = switch (c) {
            case Alias a -> {
                p.put("id", a.targetId());
                p.put("name", normalize(a.name()));
                p.put("language", a.language());
                yield "MATCH (e:Kb) WHERE (e:Crop OR e:Organism) AND e.id = $id "
                        + "CREATE (:Kb:Alias {name: $name, language: $language, spaceId: $spaceId, "
                        + "proposalId: $proposalId, provenance: $provenance, sources: $sources})-[:NAME_OF]->(e)";
            }
            case Pair pair -> {
                p.put("a", pair.cropId());
                p.put("b", pair.partnerId());
                p.put("effect", pair.effect().trim());
                // The relationship type comes from the validated set, never from the input.
                String type = KnowledgeValidator.RELATIONS.stream().filter(pair.relation()::equals).findFirst()
                        .orElseThrow();
                yield "MATCH (a:Kb:Crop {id: $a}), (b:Kb:Crop {id: $b}) CREATE (a)-[:" + type + "_OF {effect: $effect, "
                        + "evidence: 'contributed', sources: $sources, provenance: $provenance, spaceId: $spaceId, "
                        + "proposalId: $proposalId}]->(b)";
            }
            case Host h -> {
                p.put("o", h.organismId());
                p.put("c", h.cropId());
                p.put("from", h.fromMonth());
                p.put("to", h.toMonth());
                yield "MATCH (o:Kb:Organism {id: $o}), (c:Kb:Crop {id: $c}) CREATE (o)-[:AFFECTS {fromMonth: $from, "
                        + "toMonth: $to, sources: $sources, verified: false, provenance: $provenance, "
                        + "spaceId: $spaceId, proposalId: $proposalId}]->(c)";
            }
        };
        try (var session = driver.session()) {
            int created = session.executeWrite(tx -> tx.run(query, p).consume().counters().relationshipsCreated());
            if (created != 1) {
                throw new IllegalStateException("Contribution " + proposalId + " did not match the knowledge base");
            }
        }
    }

    /** Removes what a contribution wrote (a withdrawn space fact). */
    public void remove(long proposalId) {
        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH ()-[r]->() WHERE r.proposalId = $id DELETE r", Map.of("id", proposalId)).consume();
                return tx.run("MATCH (a:Kb:Alias {proposalId: $id}) DETACH DELETE a", Map.of("id", proposalId))
                        .consume();
            });
        }
    }

    static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** Global facts, plus this space's own when a space is given. */
    private static String visible(String v) {
        return "(" + v + ".spaceId IS NULL OR " + v + ".spaceId = $space)";
    }

    private static Map<String, Object> params(Long spaceId, Object... keysAndValues) {
        Map<String, Object> m = new HashMap<>();
        m.put("space", spaceId == null ? -1L : spaceId);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            m.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return m;
    }

    private void crop(String id, List<String> problems) {
        if (!exists("MATCH (c:Kb:Crop {id: $id}) RETURN count(c) > 0 AS ok", Map.of("id", nn(id)))) {
            problems.add("unknown crop " + id);
        }
    }

    private static void month(int month, List<String> problems) {
        if (month < 1 || month > 12) {
            problems.add("month " + month + " is not between 1 and 12");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private boolean exists(String query, Map<String, Object> params) {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run(query, params).single().get("ok").asBoolean());
        }
    }

    private List<String> ids(String query, Map<String, Object> params) {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run(query, params).list(r -> r.get("id").asString()));
        }
    }
}
