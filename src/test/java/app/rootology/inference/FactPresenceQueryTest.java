package app.rootology.inference;

import app.rootology.ontology.OntologyDefinition;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

class FactPresenceQueryTest {

    @Test
    void reportsMissingFactAsEmpty() {
        // Mosquito has no declared activeInSeason facts in OntologyDefinition.
        var modelService = new JenaModelService(new OntologyDefinition());
        var factPresenceQuery = new FactPresenceQuery(modelService);

        Optional<List<String>> seasons = factPresenceQuery.activeSeasonsOf("Mosquito");

        assertThat(seasons).isEmpty();
    }
}
