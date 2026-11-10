package io.github.vincenzofiore.rootology.facts;

/**
 * A crop of the knowledge base: a stable id, names, botanical family, its records in Wikidata and FAO
 * ECOCROP (ecocropId is null when ECOCROP has no record for it), and its frost group (#62) for the
 * sowing window rule.
 */
public record Crop(String id, String italianName, String englishName, String scientificName, String family,
                   String wikidataId, String ecocropId, String frostGroup) {
}
