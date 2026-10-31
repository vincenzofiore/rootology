package io.github.vincenzofiore.rootology.pipeline;

/**
 * How much the pipeline's input readings are degraded. The arbiter never sees this.
 *
 * @param dropout   probability that each true fact is missing
 * @param spurious  number of false facts added about the question's subject
 * @param mislabel  probability that a reading carries the wrong source tag
 *                  (a true fact marked UNCONFIRMED, or a false one marked CURATED)
 * @param seed      makes a degraded input reproducible
 */
public record NoiseProfile(double dropout, int spurious, double mislabel, long seed) {

    public static final NoiseProfile NONE = new NoiseProfile(0, 0, 0, 0);
}
