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
 * Rebuilds Neo4j at startup from the two sources of truth, which never mix:
 * the quality suite's dataset from {@link OntologyDefinition} (label `Suite`), and the curated
 * knowledge base from `src/main/resources/knowledge/` (label `Kb`). The knowledge base is validated
 * first: if it has problems, the app does not start and every problem is listed.
 */
@Component
class FactGraphLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FactGraphLoader.class);

    private final Driver driver;
    private final OntologyDefinition ontology;
    private final CropCatalog crops;

    FactGraphLoader(Driver driver, OntologyDefinition ontology, CropCatalog crops) {
        this.driver = driver;
        this.ontology = ontology;
        this.crops = crops;
    }

    @Override
    public void run(ApplicationArguments args) {
        load();
    }

    void load() {
        CuratedKnowledge knowledge = CuratedKnowledgeReader.read(crops);
        List<String> problems = KnowledgeValidator.validate(knowledge);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The curated knowledge base is invalid:\n  - "
                    + String.join("\n  - ", problems));
        }
        List<Fact> suite = OntologyFacts.from(ontology);
        KnowledgeGraphLoader.createConstraints(driver);
        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (n) DETACH DELETE n").consume();
                for (Fact fact : suite) {
                    tx.run(Predicate.of(fact.predicate()).mergeQuery(),
                            Map.of("subject", fact.subject(), "object", fact.object())).consume();
                }
                KnowledgeGraphLoader.load(tx, knowledge);
                return null;
            });
        }
        log.info("Loaded {} suite facts and the knowledge base ({} crops, {} pairs, {} pests and diseases) into Neo4j",
                suite.size(), knowledge.crops().size(), knowledge.pairs().size(), knowledge.organisms().size());
    }
}
