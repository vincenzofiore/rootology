package app.rootology.usage;

/**
 * QUESTION: a question asked (limited per day). REVIEW: an answer to an escalation (recorded, never blocked).
 * CONTINUATION: the same question again after the person chose a place or a garden (#107), recorded and
 * never counted by the daily limit.
 */
public enum UsageKind {
    QUESTION, REVIEW, CONTINUATION
}
