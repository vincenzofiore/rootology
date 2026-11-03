package io.github.vincenzofiore.rootology.decision;

/**
 * Structured reading of a question, extracted from the text by Claude
 * (or supplied directly to bypass that step).
 *
 * @param season only used by {@link QueryKind#RELEVANT_PESTS}, otherwise null
 * @param language ISO 639-1 code of the question's language (for example "it"), or null; it chooses the
 *                 names of the knowledge base's facts and the language of the answer, never the decision
 */
public record Intent(QueryKind kind, String subject, String season, String language) {

    /** Without a language: the suite's fixed intents, answered in English. */
    public Intent(QueryKind kind, String subject, String season) {
        this(kind, subject, season, null);
    }

    public static Intent of(QueryKind kind, String subject) {
        return new Intent(kind, subject, null, null);
    }

    /** The question's language as an ISO 639-1 code, English when unknown. */
    public String languageOrDefault() {
        return language == null || language.isBlank() ? "en" : language.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
