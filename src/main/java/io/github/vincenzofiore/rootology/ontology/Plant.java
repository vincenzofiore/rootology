package io.github.vincenzofiore.rootology.ontology;

import java.util.List;

public record Plant(
        String name,
        List<SoilType> compatibleSoils,
        List<Season> growingSeasons,
        List<String> companionPlants,
        List<String> repelledPests
) {}
