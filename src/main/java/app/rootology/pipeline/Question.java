package app.rootology.pipeline;

/** A question as posed to every contestant, keyed by experiment case id (e.g. "easy-1"). */
public record Question(String id, String text) {

    /** The longest question a person may ask (#41): enough for a garden question, bounded for Claude. */
    public static final int MAX_LENGTH = 500;
}
