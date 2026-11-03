package io.github.vincenzofiore.rootology.facts;

import java.util.Arrays;

/** How each fact predicate is stored in Neo4j: relationship type and the labels of its two ends. */
enum Predicate {

    COMPANION_OF("companionOf", "Plant", "Plant"),
    REPELS("repels", "Plant", "Pest"),
    GROWS_IN_SEASON("growsInSeason", "Plant", "Season"),
    COMPATIBLE_WITH_SOIL("compatibleWithSoil", "Plant", "Soil"),
    ACTIVE_IN_SEASON("activeInSeason", "Pest", "Season");

    final String name;
    final String subjectLabel;
    final String objectLabel;

    Predicate(String name, String subjectLabel, String objectLabel) {
        this.name = name;
        this.subjectLabel = subjectLabel;
        this.objectLabel = objectLabel;
    }

    static Predicate of(String name) {
        return Arrays.stream(values()).filter(p -> p.name.equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown predicate " + name));
    }

    static Predicate ofRelationship(String type) {
        return valueOf(type);
    }

    /** Labels and the relationship type come from this enum, never from input, so the query is safe to build. */
    String mergeQuery() {
        return "MERGE (s:%s {name: $subject}) MERGE (o:%s {name: $object}) MERGE (s)-[:%s]->(o)"
                .formatted(subjectLabel, objectLabel, name());
    }
}
