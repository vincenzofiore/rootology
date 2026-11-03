package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Rebuilds the Neo4j graph from {@link OntologyDefinition} at startup, which stays the single
 * source of truth (the database is wiped first, like the in-memory Jena graph is rebuilt).
 */
@Component
class FactGraphLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FactGraphLoader.class);

    private final Driver driver;
    private final OntologyDefinition ontology;

    FactGraphLoader(Driver driver, OntologyDefinition ontology) {
        this.driver = driver;
        this.ontology = ontology;
    }

    @Override
    public void run(ApplicationArguments args) {
        load();
    }

    void load() {
        List<Fact> facts = OntologyFacts.from(ontology);
        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (n) DETACH DELETE n").consume();
                for (Fact fact : facts) {
                    tx.run(Predicate.of(fact.predicate()).mergeQuery(),
                            Map.of("subject", fact.subject(), "object", fact.object())).consume();
                }
                return null;
            });
        }
        log.info("Loaded {} facts into Neo4j", facts.size());
    }
}
