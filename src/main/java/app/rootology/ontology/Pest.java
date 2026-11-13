package app.rootology.ontology;

import java.util.List;

public record Pest(
        String name,
        List<Season> activeSeasons
) {}
