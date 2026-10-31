package io.github.vincenzofiore.rootology.decision;

/**
 * Structured reading of a question, extracted from the text by Claude
 * (or supplied directly to bypass that step).
 *
 * @param season only used by {@link QueryKind#RELEVANT_PESTS}, otherwise null
 */
public record Intent(QueryKind kind, String subject, String season) {

    public static Intent of(QueryKind kind, String subject) {
        return new Intent(kind, subject, null);
    }
}
