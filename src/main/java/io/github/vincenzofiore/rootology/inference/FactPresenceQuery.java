package io.github.vincenzofiore.rootology.inference;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Query used to explicitly check whether a fact is present in the graph,
 * rather than silently returning an empty list when data is missing.
 * This mirrors the "hard-2" experiment case: Mosquito has no declared
 * activeInSeason facts, and we want the system to say so explicitly
 * instead of implying "active in no season" or guessing.
 */
@Component
public class FactPresenceQuery {

    private final JenaModelService modelService;

    public FactPresenceQuery(JenaModelService modelService) {
        this.modelService = modelService;
    }

    /** Returns the declared seasons for a pest, or empty if none are declared at all. */
    public Optional<List<String>> activeSeasonsOf(String pestName) {
        Model model = modelService.getModel();

        String queryString = """
            PREFIX ro: <http://rootology.dev/ontology#>
            SELECT ?seasonName WHERE {
                <%s> ro:activeInSeason ?season .
                BIND(REPLACE(STR(?season), ".*/", "") AS ?seasonName)
            }
            """.formatted(Vocabulary.pest(pestName));

        List<String> seasons = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(queryString, model)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                seasons.add(results.nextSolution().getLiteral("seasonName").getString());
            }
        }

        // Empty result is treated as "fact not declared", not "declared as empty".
        return seasons.isEmpty() ? Optional.empty() : Optional.of(seasons);
    }
}
