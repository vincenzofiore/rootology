package io.github.vincenzofiore.rootology.decision;

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
     * with probability 1.
     */
    record Verdict(boolean refused, double probability) {

        public static Verdict refusing(double probability) {
            return new Verdict(true, probability);
        }

        public static Verdict allowing(double probability) {
            return new Verdict(false, probability);
        }
    }

    Verdict judge(String question);
}
