package app.rootology.facts;

/**
 * A crop of the knowledge base: a stable id, names, botanical family, its records in Wikidata and FAO
 * ECOCROP (ecocropId is null when ECOCROP has no record for it), and its frost group (#62) for the
 * sowing window rule, and the Italian definite article of its name (#111), so a sentence can name it.
 */
public record Crop(String id, String italianName, String englishName, String scientificName, String family,
                   String wikidataId, String ecocropId, String frostGroup, String italianArticle) {

    /** The articles a crop's Italian name may take. */
    public static final java.util.Set<String> ITALIAN_ARTICLES =
            java.util.Set.of("il", "lo", "la", "l’", "i", "gli", "le");

    /**
     * The Italian name as a sentence says it (#111): with its article and in lower case, "la fragola",
     * "l’aglio", "le cime di rapa".
     */
    public String italianInSentence() {
        String name = italianName.isEmpty() ? italianName
                : Character.toLowerCase(italianName.charAt(0)) + italianName.substring(1);
        if (italianArticle == null || italianArticle.isEmpty()) {
            return name;
        }
        return italianArticle.endsWith("’") ? italianArticle + name : italianArticle + " " + name;
    }
}
