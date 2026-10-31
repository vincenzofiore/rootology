package io.github.vincenzofiore.rootology.inference.facts;

/** One declared triple, with URIs reduced to local names (e.g. "Tomato", "repels", "Aphid"). */
public record Fact(String subject, String predicate, String object) {
}
