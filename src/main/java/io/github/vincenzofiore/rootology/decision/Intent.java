package io.github.vincenzofiore.rootology.decision;

/**
 * Structured reading of a question, extracted from the text by Claude
 * (or supplied directly to bypass that step).
 *
 * @param season only used by {@link QueryKind#RELEVANT_PESTS}, otherwise null
 * @param language ISO 639-1 code of the question's language (for example "it"), or null; it chooses the
 *                 names of the knowledge base's facts and the language of the answer, never the decision
 * @param zone     NORD, CENTRO or SUD, for the knowledge base's sowing and harvest windows
 * @param month    JANUARY to DECEMBER, for {@link QueryKind#SOW_NOW}
 * @param garden   the name of one of the space's gardens as written in the question (#57), or null; only the
 *                 context resolver reads it, never the decision layer
 */
public record Intent(QueryKind kind, String subject, String season, String language, String zone, String month,
                     String garden) {

    /** Without a garden named: every intent before #57, and the suite's. */
    public Intent(QueryKind kind, String subject, String season, String language, String zone, String month) {
        this(kind, subject, season, language, zone, month, null);
    }

    /** Without a language: the suite's fixed intents, answered in English. */
    public Intent(QueryKind kind, String subject, String season) {
        this(kind, subject, season, null, null, null);
    }

    public Intent(QueryKind kind, String subject, String season, String language) {
        this(kind, subject, season, language, null, null);
    }

    public static Intent of(QueryKind kind, String subject) {
        return new Intent(kind, subject, null, null, null, null);
    }

    public Intent withZoneAndMonth(String zone, String month) {
        return new Intent(kind, subject, season, language, zone, month, garden);
    }

    /** The same intent naming no garden: what Claude returns when it was given no garden names (#57). */
    public Intent withoutGarden() {
        return garden == null ? this : new Intent(kind, subject, season, language, zone, month, null);
    }

    /** The same intent about another subject, for example the plot a question named as it is really called. */
    public Intent withSubject(String subject) {
        return new Intent(kind, subject, season, language, zone, month, garden);
    }

    /**
     * The same intent with every text trimmed, stripped of control characters and cut to a sane length
     * (#41). Values are not otherwise changed: an unknown zone or month still finds no facts and escalates,
     * rather than turning into a default.
     */
    public Intent bounded() {
        return new Intent(kind, bound(subject, 100), bound(season, 20), bound(language, 5), bound(zone, 20),
                bound(month, 20), bound(garden, 100));
    }

    private static String bound(String text, int max) {
        if (text == null) {
            return null;
        }
        String plain = text.replaceAll("\\p{Cntrl}", " ").strip();
        return plain.length() <= max ? plain : plain.substring(0, max);
    }

    /** The question's language as an ISO 639-1 code, English when unknown. */
    public String languageOrDefault() {
        return language == null || language.isBlank() ? "en" : language.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
