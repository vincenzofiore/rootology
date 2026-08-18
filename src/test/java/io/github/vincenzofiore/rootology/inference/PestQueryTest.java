package io.github.vincenzofiore.rootology.inference;

import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PestQueryTest {

    @Test
    void findsPestsActiveInSummerNotRepelledByBasil() {
        // Basil repels both Mosquito and Aphid, so in summer
        // only pests it does NOT repel should remain relevant.
        var modelService = new JenaModelService(new OntologyDefinition());
        var pestQuery = new PestQuery(modelService);

        List<String> pests = pestQuery.findRelevantPests("Basil", "SUMMER");

        // Aphid is active in summer but repelled by Basil -> excluded.
        assertThat(pests).isNotEmpty().doesNotContain("Aphid");
    }

    @Test
    void findsPestsActiveInSummerForTomato() {
        // Tomato repels Aphid, so Aphid should be excluded even though
        // it's active in summer.
        var modelService = new JenaModelService(new OntologyDefinition());
        var pestQuery = new PestQuery(modelService);

        List<String> pests = pestQuery.findRelevantPests("Tomato", "SUMMER");

        assertThat(pests).isNotEmpty().doesNotContain("Aphid");
    }
}
