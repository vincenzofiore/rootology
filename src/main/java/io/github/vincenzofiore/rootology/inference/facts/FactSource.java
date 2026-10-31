package io.github.vincenzofiore.rootology.inference.facts;

import io.github.vincenzofiore.rootology.inference.JenaModelService;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hands out the raw triples of the in-memory graph. Deliberately does no reasoning:
 * the arbiter runs the SPARQL queries, the pipeline gets only what is declared.
 */
@Component
public class FactSource {

    private final JenaModelService modelService;

    public FactSource(JenaModelService modelService) {
        this.modelService = modelService;
    }

    public List<Fact> allFacts() {
        return modelService.getModel().listStatements().toList().stream()
                .map(FactSource::toFact)
                .toList();
    }

    private static Fact toFact(Statement st) {
        return new Fact(
                localName(st.getSubject()),
                localName(st.getPredicate()),
                localName(st.getObject()));
    }

    private static String localName(RDFNode node) {
        String uri = node.asResource().getURI();
        return uri.substring(Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('#')) + 1);
    }
}
