package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.TestcontainersConfiguration;
import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** Neo4j, loaded at startup, returns exactly the declared triples of the closed dataset. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FactSourceTest {

    @Autowired
    FactSource factSource;

    @Autowired
    OntologyDefinition ontology;

    @Test
    void neo4jHoldsTheDeclaredTriples() {
        assertThat(factSource.allFacts()).containsExactlyElementsOf(OntologyFacts.from(ontology));
    }
}
