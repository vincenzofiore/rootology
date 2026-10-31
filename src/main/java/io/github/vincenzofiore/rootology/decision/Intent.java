package io.github.vincenzofiore.rootology.decision;

/**
 * Structured reading of a question. Supplied directly for now; Claude will extract it
 * from the question text in a later step.
 *
 * @param season only used by {@link QueryKind#RELEVANT_PESTS}, otherwise null
 */
public record Intent(QueryKind kind, String subject, String season) {

    public static Intent of(QueryKind kind, String subject) {
        return new Intent(kind, subject, null);
    }
}
