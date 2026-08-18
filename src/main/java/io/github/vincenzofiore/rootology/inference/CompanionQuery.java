package io.github.vincenzofiore.rootology.inference;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CompanionQuery {

    private final JenaModelService modelService;

    public CompanionQuery(JenaModelService modelService) {
        this.modelService = modelService;
    }

    public List<String> findCompanionsOf(String plantName) {
        Model model = modelService.getModel();

        String queryString = """
            PREFIX ro: <http://rootology.dev/ontology#>
            SELECT ?companionName WHERE {
                ?plant ro:companionOf ?companion .
                FILTER(?plant = <%s>)
                BIND(REPLACE(STR(?companion), ".*/", "") AS ?companionName)
            }
            """.formatted(Vocabulary.plant(plantName));

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
