package app.rootology.decision;

/**
 * Decides whether a question is clearly outside vegetable gardens and small-scale growing (#53), so a
 * reviewer is not asked about a question that was never meant for this app. Deciding what belongs to the
 * domain is a decision, so it is Jev's, like every other. Only a clear no is refused: a wrong refusal costs
 * more than a useless escalation, so everything uncertain takes the normal path.
 */
public interface DomainFilter {

    /**
     * Jev's probability (0 to 1) that the question is about gardens and growing, and whether that is low
     * enough to refuse it. A question that was never judged, because Jev could not be reached, is allowed
     * with probability 1 and judged false, so the quality suite can tell an outage from a wrong pass (#161).
     */
    record Verdict(boolean refused, double probability, boolean judged) {

        public static Verdict refusing(double probability) {
            return new Verdict(true, probability, true);
        }

        public static Verdict allowing(double probability) {
            return new Verdict(false, probability, true);
        }

        /** Jev could not be asked or did not answer: the question goes on, never refused on a failure. */
        public static Verdict unjudged() {
            return new Verdict(false, 1.0, false);
        }
    }

    Verdict judge(String question);
}
