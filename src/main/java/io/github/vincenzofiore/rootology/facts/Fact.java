package io.github.vincenzofiore.rootology.facts;

/** One declared triple, with URIs reduced to local names (e.g. "Tomato", "repels", "Aphid"). */
public record Fact(String subject, String predicate, String object) {
}
