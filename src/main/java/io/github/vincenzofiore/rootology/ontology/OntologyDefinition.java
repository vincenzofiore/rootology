package io.github.vincenzofiore.rootology.ontology;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OntologyDefinition {

    public List<Plant> plants() {
        return List.of(
                new Plant("Tomato",
                        List.of(SoilType.LOAMY, SoilType.CLAY),
                        List.of(Season.SPRING, Season.SUMMER),
                        List.of("Basil", "Marigold"),
                        List.of("Aphid")),
                new Plant("Basil",
                        List.of(SoilType.LOAMY),
                        List.of(Season.SPRING, Season.SUMMER),
                        List.of("Tomato"),
                        List.of("Mosquito", "Aphid")),
                new Plant("Marigold",
                        List.of(SoilType.SANDY, SoilType.LOAMY, SoilType.CLAY),
                        List.of(Season.SPRING, Season.SUMMER, Season.AUTUMN),
                        List.of("Tomato"),
                        List.of("Nematode"))
        );
    }

    public List<Pest> pests() {
        return List.of(
                new Pest("Aphid", List.of(Season.SPRING, Season.SUMMER)),
                new Pest("Nematode", List.of(Season.SUMMER, Season.AUTUMN))
        );
    }
}
