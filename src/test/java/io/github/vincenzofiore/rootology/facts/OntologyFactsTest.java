package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.inference.JenaModelService;
import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import org.apache.jena.rdf.model.RDFNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The triples the pipeline gets now (from OntologyDefinition, via Neo4j) are exactly the ones it
 * got before #7 from the Jena graph, in the same order, so the recorded runs stay comparable.
 */
class OntologyFactsTest {

    @Test
    void matchesTheTriplesOfTheJenaGraph() {
        OntologyDefinition ontology = new OntologyDefinition();
        List<Fact> fromJena = new JenaModelService(ontology).getModel().listStatements().toList().stream()
                .map(st -> new Fact(localName(st.getSubject()), localName(st.getPredicate()), localName(st.getObject())))
                .sorted(OntologyFacts.ORDER)
                .toList();

        assertThat(OntologyFacts.from(ontology)).containsExactlyElementsOf(fromJena);
    }

    private static String localName(RDFNode node) {
        String uri = node.asResource().getURI();
        return uri.substring(Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('#')) + 1);
    }
}
