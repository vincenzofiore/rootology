package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import io.github.vincenzofiore.rootology.ontology.Pest;
import io.github.vincenzofiore.rootology.ontology.Plant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The declared triples of the closed dataset, straight from {@link OntologyDefinition}, sorted.
 * Loaded into Neo4j at startup, and used as is by tests that run without a database.
 */
public final class OntologyFacts {

    static final Comparator<Fact> ORDER = Comparator.comparing(Fact::subject)
            .thenComparing(Fact::predicate)
            .thenComparing(Fact::object);

    public static List<Fact> from(OntologyDefinition ontology) {
        List<Fact> facts = new ArrayList<>();
        for (Plant plant : ontology.plants()) {
            plant.growingSeasons().forEach(s -> facts.add(new Fact(plant.name(), "growsInSeason", s.name())));
            plant.compatibleSoils().forEach(s -> facts.add(new Fact(plant.name(), "compatibleWithSoil", s.name())));
            plant.companionPlants().forEach(c -> facts.add(new Fact(plant.name(), "companionOf", c)));
            plant.repelledPests().forEach(p -> facts.add(new Fact(plant.name(), "repels", p)));
        }
        for (Pest pest : ontology.pests()) {
            pest.activeSeasons().forEach(s -> facts.add(new Fact(pest.name(), "activeInSeason", s.name())));
        }
        facts.sort(ORDER);
        return List.copyOf(facts);
    }

    private OntologyFacts() {
    }
}
