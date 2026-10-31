package io.github.vincenzofiore.rootology.decision;

/** Decides an answer from raw facts. Jev will sit behind this interface. */
public interface DecisionLayer {

    Decision decide(Intent intent, GraphFacts facts);
}
