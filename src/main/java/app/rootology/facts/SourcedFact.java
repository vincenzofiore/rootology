package app.rootology.facts;

import java.util.List;

/**
 * A fact from the knowledge base with the ids of the sources it rests on (see `sources.csv`, or
 * `proposal-<id>` for a contributed fact); derivedBy names the rule when the fact was inferred (for
 * example frost-shift-v1), otherwise null; proposed is true for a fact added by a member of the asking
 * space and not reviewed by a curator (#24).
 */
public record SourcedFact(Fact fact, List<String> sourceIds, String derivedBy, boolean proposed) {

    public SourcedFact {
        sourceIds = List.copyOf(sourceIds);
    }

    public SourcedFact(Fact fact, List<String> sourceIds, String derivedBy) {
        this(fact, sourceIds, derivedBy, false);
    }

    public SourcedFact(Fact fact, List<String> sourceIds) {
        this(fact, sourceIds, null, false);
    }
}
