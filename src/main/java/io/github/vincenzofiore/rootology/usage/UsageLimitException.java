package io.github.vincenzofiore.rootology.usage;

/** The person has used today's questions; the message is fit to show them. */
public class UsageLimitException extends RuntimeException {

    public UsageLimitException(String message) {
        super(message);
    }
}
