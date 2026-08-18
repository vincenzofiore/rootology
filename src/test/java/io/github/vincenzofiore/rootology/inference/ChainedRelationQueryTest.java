package io.github.vincenzofiore.rootology.inference;

import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChainedRelationQueryTest {

    @Test
    void findsCompanionsThroughSharedRepelledPest() {
        // Tomato repels Aphid; Basil also repels Aphid; Basil's companion is Tomato.
        // So the expected chained result is: Tomato (via Basil).
        var modelService = new JenaModelService(new OntologyDefinition());
        var chainedQuery = new ChainedRelationQuery(modelService);

        List<String> result = chainedQuery.findCompanionsOfPlantsSharingRepelledPest("Tomato");

        assertThat(result).containsExactly("Tomato");
    }
}
