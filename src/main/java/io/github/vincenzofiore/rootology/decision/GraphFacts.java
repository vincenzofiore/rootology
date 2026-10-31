package io.github.vincenzofiore.rootology.decision;

import io.github.vincenzofiore.rootology.inference.facts.Fact;

import java.util.List;

/** Raw declared triples handed to the decision layer, with plain lookups and no inference. */
public record GraphFacts(List<Fact> facts) {

    public List<String> objects(String subject, String predicate) {
        return facts.stream()
                .filter(f -> f.subject().equals(subject) && f.predicate().equals(predicate))
                .map(Fact::object)
                .toList();
    }

    public List<String> subjects(String predicate, String object) {
        return facts.stream()
                .filter(f -> f.predicate().equals(predicate) && f.object().equals(object))
                .map(Fact::subject)
                .toList();
    }

    /** True if the entity appears anywhere in the dataset, as subject or object. */
    public boolean mentions(String entity) {
        return facts.stream().anyMatch(f -> f.subject().equals(entity) || f.object().equals(entity));
    }
}
