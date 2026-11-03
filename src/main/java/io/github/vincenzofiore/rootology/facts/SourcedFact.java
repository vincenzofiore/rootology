package io.github.vincenzofiore.rootology.facts;

import java.util.List;

/** A fact from the knowledge base with the ids of the sources it rests on (see `sources.csv`). */
public record SourcedFact(Fact fact, List<String> sourceIds) {

    public SourcedFact {
        sourceIds = List.copyOf(sourceIds);
    }
}
