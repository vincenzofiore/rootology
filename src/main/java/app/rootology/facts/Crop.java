package app.rootology.facts;

/**
 * A crop of the knowledge base: a stable id, names, botanical family, its records in Wikidata and FAO
 * ECOCROP (ecocropId is null when ECOCROP has no record for it), and its frost group (#62) for the
 * sowing window rule, and the Italian definite article of its name (#111), so a sentence can name it.
 * lifecycle is ANNUAL or PERENNIAL (#139); frostHardiness is the lowest air temperature an established perennial
 * stands, from hardinessSource, or null when not known.
 * use is FOOD or SERVICE (#140): a service plant (a companion flower or a green manure) is not grown to be eaten,
 * so it is kept out of the "what can I sow now" answers while its own calendar still answers.
 */
public record Crop(String id, String italianName, String englishName, String scientificName, String family,
                   String wikidataId, String ecocropId, String frostGroup, String italianArticle, String lifecycle,
                   Double frostHardiness, String hardinessSource, String use) {

    public static final java.util.Set<String> LIFECYCLES = java.util.Set.of("ANNUAL", "PERENNIAL");

    /** FOOD (the default of the knowledge base) or SERVICE (#140). */
    public static final java.util.Set<String> USES = java.util.Set.of("FOOD", "SERVICE");

    /** Stays in place for years: out of the rotation rule, and judged on its own frost hardiness once established. */
    public boolean perennial() {
        return "PERENNIAL".equals(lifecycle);
    }

    /** A service plant or green manure: not food, so not an answer to "what can I sow now" (#140). */
    public boolean service() {
        return "SERVICE".equals(use);
    }

    /** The name in a page's language: English for an English page, Italian otherwise (#182). */
    public String name(java.util.Locale locale) {
        return locale != null && "en".equals(locale.getLanguage()) ? englishName : italianName;
    }

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
