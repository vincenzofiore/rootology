package io.github.vincenzofiore.rootology.inference;

import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionQueryTest {

    @Test
    void findsCompanionsOfTomato() {

        var modelService = new JenaModelService(new OntologyDefinition());
        var companionQuery = new CompanionQuery(modelService);

        List<String> companions = companionQuery.findCompanionsOf("Tomato");

        assertThat(companions).containsExactlyInAnyOrder("Basil", "Marigold");
    }
}
