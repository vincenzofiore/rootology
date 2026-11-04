package io.github.vincenzofiore.rootology.usage;

/** The person has used today's questions; the message is fit to show them. */
public class UsageLimitException extends RuntimeException {

    private final int limit;

    public UsageLimitException(String message, int limit) {
        super(message);
        this.limit = limit;
    }

    /** Today's limit, for the UI to word the message in the person's language (#31). */
    public int limit() {
        return limit;
    }
}
