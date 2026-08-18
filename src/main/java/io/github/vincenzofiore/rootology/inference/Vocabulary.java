package io.github.vincenzofiore.rootology.inference;

public final class Vocabulary {

    public static final String NS = "http://rootology.dev/ontology#";

    // properties
    public static final String COMPANION_OF = NS + "companionOf";
    public static final String REPELS = NS + "repels";
    public static final String GROWS_IN_SEASON = NS + "growsInSeason";
    public static final String COMPATIBLE_WITH_SOIL = NS + "compatibleWithSoil";
    public static final String ACTIVE_IN_SEASON = NS + "activeInSeason";

    public static String plant(String name) {
        return NS + "plant/" + name;
    }

    public static String pest(String name) {
        return NS + "pest/" + name;
    }

    public static String season(String name) {
        return NS + "season/" + name;
    }

    public static String soil(String name) {
        return NS + "soil/" + name;
    }

    private Vocabulary() {}
}
