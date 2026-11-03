package io.github.vincenzofiore.rootology.facts;

import java.util.List;

/**
 * A fact from the knowledge base with the ids of the sources it rests on (see `sources.csv`);
 * derivedBy names the rule when the fact was inferred (for example zone-shift-v1), otherwise null.
 */
public record SourcedFact(Fact fact, List<String> sourceIds, String derivedBy) {

    public SourcedFact {
        sourceIds = List.copyOf(sourceIds);
    }

    public SourcedFact(Fact fact, List<String> sourceIds) {
        this(fact, sourceIds, null);
    }
}
