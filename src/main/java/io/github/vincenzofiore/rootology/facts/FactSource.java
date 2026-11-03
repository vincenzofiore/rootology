package io.github.vincenzofiore.rootology.facts;

import org.neo4j.driver.Driver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hands out the raw triples stored in Neo4j. Deliberately does no reasoning:
 * the arbiter runs SPARQL on its own Jena graph, the pipeline gets only what is declared.
 */
@Component
public class FactSource {

    private final Driver driver;

    public FactSource(Driver driver) {
        this.driver = driver;
    }

    /** Sorted, so the decision layer always receives the facts in the same order. */
    public List<Fact> allFacts() {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run("MATCH (s)-[r]->(o) RETURN s.name AS s, type(r) AS r, o.name AS o")
                    .list(row -> new Fact(row.get("s").asString(),
                            Predicate.ofRelationship(row.get("r").asString()).name,
                            row.get("o").asString())))
                    .stream()
                    .sorted(OntologyFacts.ORDER)
                    .toList();
        }
    }
}
