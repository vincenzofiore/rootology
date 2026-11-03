package io.github.vincenzofiore.rootology.facts;

/** A crop of the knowledge base: a stable id plus names and botanical family. */
public record Crop(String id, String italianName, String englishName, String scientificName, String family) {
}
