package io.github.vincenzofiore.rootology.inference;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Query for the "hard" multi-hop experiment case: find companions of any
 * plant that repels the same pest repelled by a given reference plant.
 *
 * Chain: referencePlant --repels--> pest <--repels-- otherPlant --companionOf--> companion
 */
@Component
public class ChainedRelationQuery {

    private final JenaModelService modelService;

    public ChainedRelationQuery(JenaModelService modelService) {
        this.modelService = modelService;
    }

    public List<String> findCompanionsOfPlantsSharingRepelledPest(String referencePlantName) {
        Model model = modelService.getModel();

        // Three hops in one SPARQL query: shared pest -> other plant -> its companion.
        // The reference plant itself is excluded from "other plants" to avoid
        // trivially matching against itself.
        String queryString = """
            PREFIX ro: <http://rootology.dev/ontology#>
            SELECT DISTINCT ?companionName WHERE {
                <%s> ro:repels ?pest .
                ?otherPlant ro:repels ?pest .
                FILTER(?otherPlant != <%s>)
                ?otherPlant ro:companionOf ?companion .
                BIND(REPLACE(STR(?companion), ".*/", "") AS ?companionName)
            }
            """.formatted(
                Vocabulary.plant(referencePlantName),
                Vocabulary.plant(referencePlantName)
        );

        List<String> result = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(queryString, model)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution sol = results.nextSolution();
                result.add(sol.getLiteral("companionName").getString());
            }
        }
        return result;
    }
}
