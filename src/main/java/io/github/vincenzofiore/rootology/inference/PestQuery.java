package io.github.vincenzofiore.rootology.inference;


import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Query to find pests that are active in a given season for a given plant,
 * excluding pests the plant itself is known to repel.
 */
@Component
public class PestQuery {

    private final JenaModelService modelService;

    public PestQuery(JenaModelService modelService) {
        this.modelService = modelService;
    }

    public List<String> findRelevantPests(String plantName, String seasonName) {
        Model model = modelService.getModel();

        // SPARQL query: select pests active in the given season,
        // excluding pests already repelled by the given plant.
        String queryString = """
            PREFIX ro: <http://rootology.dev/ontology#>
            SELECT ?pestName WHERE {
                ?pest ro:activeInSeason <%s> .
                BIND(REPLACE(STR(?pest), ".*/", "") AS ?pestName)
                FILTER NOT EXISTS {
                    <%s> ro:repels ?pest .
                }
            }
            """.formatted(
                Vocabulary.season(seasonName),
                Vocabulary.plant(plantName)
        );

        List<String> result = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(queryString, model)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution sol = results.nextSolution();
                result.add(sol.getLiteral("pestName").getString());
            }
        }
        return result;
    }
}
