package io.github.vincenzofiore.rootology.pipeline;

/**
 * How much the pipeline's input readings are degraded. The arbiter never sees this.
 *
 * @param dropout   probability that each true fact is missing
 * @param spurious  number of false facts added about the question's subject
 *                  (or, with nearSubject, about the subject and its neighbors)
 * @param mislabel  probability that a reading carries the wrong source tag
 *                  (a true fact marked UNCONFIRMED, or a false one marked CURATED)
 * @param nearSubject also draw false facts about the plants and pests that share a fact with
 *                    the subject; false keeps the original behavior, which the saved
 *                    judgments of the first noise runs depend on
 * @param seed      makes a degraded input reproducible
 */
public record NoiseProfile(double dropout, int spurious, double mislabel, boolean nearSubject, long seed) {

    public static final NoiseProfile NONE = new NoiseProfile(0, 0, 0, false, 0);
}
