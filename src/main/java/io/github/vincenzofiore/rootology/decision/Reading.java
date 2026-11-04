package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.facts.Fact;

import java.util.List;

/** A fact as the decision layer receives it, tagged with where it comes from. */
public record Reading(Fact fact, Source source, List<String> sourceIds, String derivedBy) {

    public enum Source {
        /** From the curated dataset. */
        CURATED,
        /** From a single unverified report; may be wrong. */
        UNCONFIRMED,
        /** Derived by an explicit, tested rule from curated facts; as reliable as the rule. */
        INFERRED,
        /** Added by a member of the asking space, not reviewed by a curator; plausible but may be wrong. */
        PROPOSED
    }

    /** sourceIds: where the fact comes from (ids of the knowledge base's sources); derivedBy: the rule, for INFERRED. */
    public Reading {
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }

    public Reading(Fact fact, Source source) {
        this(fact, source, List.of(), null);
    }

    public static Reading curated(Fact fact) {
        return new Reading(fact, Source.CURATED);
    }

    public static Reading inferred(Fact fact, String rule, List<String> sourceIds) {
        return new Reading(fact, Source.INFERRED, sourceIds, rule);
    }
}
