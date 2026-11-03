package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.facts.Fact;

/** A fact as the decision layer receives it, tagged with where it comes from. */
public record Reading(Fact fact, Source source) {

    public enum Source {
        /** From the curated dataset. */
        CURATED,
        /** From a single unverified report; may be wrong. */
        UNCONFIRMED
    }

    public static Reading curated(Fact fact) {
        return new Reading(fact, Source.CURATED);
    }
}
