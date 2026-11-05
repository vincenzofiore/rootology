package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.facts.Fact;

import java.util.List;
import java.util.stream.Stream;

/**
 * Readings handed to the decision layer, with plain lookups and no inference.
 * The lookups ignore the source: whether to trust a reading is the decision layer's call.
 */
public record GraphFacts(List<Reading> readings, List<Note> notes) {

    /** notes: what the person should know about these facts (for example a missing forecast); never sent to Jev. */
    public GraphFacts {
        readings = List.copyOf(readings);
        notes = List.copyOf(notes);
    }

    public GraphFacts(List<Reading> readings) {
        this(readings, List.of());
    }

    public static GraphFacts curated(List<Fact> facts) {
        return new GraphFacts(facts.stream().map(Reading::curated).toList());
    }

    public List<String> objects(String subject, String predicate) {
        return facts()
                .filter(f -> f.subject().equals(subject) && f.predicate().equals(predicate))
                .map(Fact::object)
                .toList();
    }

    public List<String> subjects(String predicate, String object) {
        return facts()
                .filter(f -> f.predicate().equals(predicate) && f.object().equals(object))
                .map(Fact::subject)
                .toList();
    }

    /** True if the entity appears anywhere, as subject or object. */
    public boolean mentions(String entity) {
        return facts().anyMatch(f -> f.subject().equals(entity) || f.object().equals(entity));
    }

    public Stream<Fact> facts() {
        return readings.stream().map(Reading::fact);
    }
}
